package com.tenacy.logpulse.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Service
@Slf4j
public class LogCompressionService {

    private final MeterRegistry meterRegistry;

    private final Counter compressionAttemptCounter;
    private final Counter compressionSuccessCounter;
    private final Counter decompressionAttemptCounter;
    private final Counter decompressionSuccessCounter;
    private final Counter compressionErrorCounter;

    private final DistributionSummary compressionRatioDistribution;
    private final Timer compressionTimer;
    private final Timer decompressionTimer;

    @Value("${logpulse.compression.enabled:true}")
    private boolean compressionEnabled;

    @Value("${logpulse.compression.min-size:1024}")
    private int minCompressionSize;

    public LogCompressionService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // 카운터 등록
        this.compressionAttemptCounter = Counter.builder("logpulse.compression.attempt.count")
                .description("Number of compression attempts")
                .register(meterRegistry);

        this.compressionSuccessCounter = Counter.builder("logpulse.compression.success.count")
                .description("Number of successful compressions")
                .register(meterRegistry);

        this.decompressionAttemptCounter = Counter.builder("logpulse.decompression.attempt.count")
                .description("Number of decompression attempts")
                .register(meterRegistry);

        this.decompressionSuccessCounter = Counter.builder("logpulse.decompression.success.count")
                .description("Number of successful decompressions")
                .register(meterRegistry);

        this.compressionErrorCounter = Counter.builder("logpulse.compression.error.count")
                .description("Number of compression/decompression errors")
                .register(meterRegistry);

        // 압축률 분포 측정 도구 등록
        this.compressionRatioDistribution = DistributionSummary.builder("logpulse.compression.ratio.distribution")
                .description("Compression ratio distribution (compressed size / original size)")
                .scale(100) // 백분율로 표시
                .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99) // 중앙값, 75%, 90%, 95%, 99% 백분위수 발행
                .register(meterRegistry);

        // 압축 시간 측정 타이머
        this.compressionTimer = Timer.builder("logpulse.compression.operation.time")
                .description("Time taken to compress log content")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        // 압축 해제 시간 측정 타이머
        this.decompressionTimer = Timer.builder("logpulse.decompression.operation.time")
                .description("Time taken to decompress log content")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    public boolean shouldCompress(String content) {
        boolean shouldCompress = compressionEnabled &&
                content != null &&
                content.length() > minCompressionSize &&
                !isCompressed(content);

        if (shouldCompress) {
            compressionAttemptCounter.increment();
        }

        // 압축 결정 요인 측정
        if (content != null) {
            meterRegistry.counter("logpulse.compression.decision",
                    "reason", content.length() <= minCompressionSize ? "too_small" :
                            isCompressed(content) ? "already_compressed" :
                                    !compressionEnabled ? "disabled" : "compressed").increment();
        }

        return shouldCompress;
    }

    public String compressContent(String content) {
        if (!shouldCompress(content)) {
            return content;
        }

        // 타이머로 압축 시간 측정
        return compressionTimer.record(() -> {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {

                gzipOut.write(content.getBytes(StandardCharsets.UTF_8));
                gzipOut.finish();

                byte[] compressedBytes = baos.toByteArray();
                String compressedContent = Base64.getEncoder().encodeToString(compressedBytes);

                // 압축률 계산 및 측정 (압축 크기 / 원본 크기)
                double compressionRatio = (double) compressedBytes.length / content.getBytes(StandardCharsets.UTF_8).length;
                compressionRatioDistribution.record(compressionRatio * 100); // 백분율로 기록

                // 원본 크기와 압축 크기 측정
                meterRegistry.counter("logpulse.compression.bytes.input").increment(content.getBytes(StandardCharsets.UTF_8).length);
                meterRegistry.counter("logpulse.compression.bytes.output").increment(compressedBytes.length);

                // 성공 카운터 증가
                compressionSuccessCounter.increment();

                log.debug("Compressed log content: original size={}, compressed size={}, ratio={}",
                        content.length(), compressedBytes.length,
                        String.format("%.2f", compressionRatio));

                return compressedContent;

            } catch (IOException e) {
                // 압축 실패 카운터 증가
                compressionErrorCounter.increment();
                meterRegistry.counter("logpulse.compression.errors", "type", "compression_failure").increment();
                log.warn("Failed to compress log content: {}", e.getMessage());
                return content;
            }
        });
    }

    public String decompressContent(String compressedContent) {
        if (compressedContent == null || compressedContent.isEmpty() || !isCompressed(compressedContent)) {
            return compressedContent;
        }

        // 압축 해제 시도 카운터 증가
        decompressionAttemptCounter.increment();

        // 타이머로 압축 해제 시간 측정
        return decompressionTimer.record(() -> {
            try {
                byte[] compressedBytes = Base64.getDecoder().decode(compressedContent);

                try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedBytes);
                     GZIPInputStream gzipIn = new GZIPInputStream(bais);
                     ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = gzipIn.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }

                    String decompressedContent = baos.toString(StandardCharsets.UTF_8.name());

                    // 압축 해제 크기 측정
                    meterRegistry.counter("logpulse.decompression.bytes.input").increment(compressedBytes.length);
                    meterRegistry.counter("logpulse.decompression.bytes.output").increment(decompressedContent.getBytes(StandardCharsets.UTF_8).length);

                    // 성공 카운터 증가
                    decompressionSuccessCounter.increment();

                    log.debug("Decompressed log content: compressed size={}, original size={}",
                            compressedBytes.length, decompressedContent.length());

                    return decompressedContent;
                }

            } catch (IllegalArgumentException e) {
                // 잘못된 Base64 형식 카운터 증가
                compressionErrorCounter.increment();
                meterRegistry.counter("logpulse.compression.errors", "type", "invalid_base64").increment();
                log.debug("Content is not Base64 encoded, returning as is");
                return compressedContent;
            } catch (IOException e) {
                // 압축 해제 실패 카운터 증가
                compressionErrorCounter.increment();
                meterRegistry.counter("logpulse.compression.errors", "type", "decompression_failure").increment();
                log.warn("Failed to decompress log content: {}", e.getMessage());
                return compressedContent;
            }
        });
    }

    public boolean isCompressed(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(content);
            return decoded.length >= 2 &&
                    (decoded[0] == (byte) 0x1f) &&
                    (decoded[1] == (byte) 0x8b);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Scheduled(fixedRate = 60000)
    public void logCompressionStats() {
        double attempts = compressionAttemptCounter.count();
        double successes = compressionSuccessCounter.count();
        double decompAttempts = decompressionAttemptCounter.count();
        double decompSuccesses = decompressionSuccessCounter.count();
        double errors = compressionErrorCounter.count();

        if (attempts > 0) {
            double successRate = (successes / attempts) * 100.0;
            double avgRatio = compressionRatioDistribution.mean();
            double p50Ratio = compressionRatioDistribution.percentile(0.5);
            double p95Ratio = compressionRatioDistribution.percentile(0.95);

            log.info("Compression operation stats - Attempts: {}, Successes: {} ({:.2f}%), " +
                            "Decompressions: {}, Errors: {}, Avg ratio: {:.2f}%, Median: {:.2f}%, 95p: {:.2f}%",
                    (long)attempts,
                    (long)successes,
                    successRate,
                    (long)decompAttempts,
                    (long)errors,
                    avgRatio,
                    p50Ratio,
                    p95Ratio);

            // 메트릭 게이지 업데이트
            meterRegistry.gauge("logpulse.compression.success.rate", successRate);
        }
    }
}