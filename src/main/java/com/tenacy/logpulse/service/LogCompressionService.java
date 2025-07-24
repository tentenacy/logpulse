package com.tenacy.logpulse.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
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
    private final Counter compressionEnabledCounter;
    private final Counter compressionDisabledCounter;

    private final DistributionSummary compressionRatioSummary;
    private final Timer compressionTimer;
    private final Timer decompressionTimer;

    @Value("${logpulse.compression.enabled:true}")
    private boolean compressionEnabled;

    @Value("${logpulse.compression.min-size:1024}")
    private int minCompressionSize;

    public LogCompressionService(MeterRegistry meterRegistry,
                                 Counter compressionEnabledCounter,
                                 Counter compressionDisabledCounter) {
        this.meterRegistry = meterRegistry;
        this.compressionEnabledCounter = compressionEnabledCounter;
        this.compressionDisabledCounter = compressionDisabledCounter;

        // 압축률 분포 측정 도구 등록 (0-1 사이의 값, 낮을수록 압축 효과 좋음)
        this.compressionRatioSummary = DistributionSummary.builder("logpulse.compression.ratio")
                .description("Compression ratio distribution (compressed size / original size)")
                .scale(100) // 백분율로 표시
                .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99) // 중앙값, 75%, 90%, 95%, 99% 백분위수 발행
                .register(meterRegistry);

        // 압축 시간 측정 타이머
        this.compressionTimer = Timer.builder("logpulse.compression.time")
                .description("Time taken to compress log content")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        // 압축 해제 시간 측정 타이머
        this.decompressionTimer = Timer.builder("logpulse.decompression.time")
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
            compressionEnabledCounter.increment();
        } else {
            compressionDisabledCounter.increment();
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

                // 압축률 계산 및 측정
                double compressionRatio = (double) compressedBytes.length / content.getBytes(StandardCharsets.UTF_8).length;
                compressionRatioSummary.record(compressionRatio);

                // 원본 크기와 압축 크기 측정
                meterRegistry.counter("logpulse.compression.bytes.original").increment(content.getBytes(StandardCharsets.UTF_8).length);
                meterRegistry.counter("logpulse.compression.bytes.compressed").increment(compressedBytes.length);

                log.debug("Compressed log content: original size={}, compressed size={}, ratio={}",
                        content.length(), compressedBytes.length,
                        String.format("%.2f", compressionRatio));

                return compressedContent;

            } catch (IOException e) {
                // 압축 실패 카운터 증가
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

                    log.debug("Decompressed log content: compressed size={}, original size={}",
                            compressedBytes.length, decompressedContent.length());

                    return decompressedContent;
                }

            } catch (IllegalArgumentException e) {
                // 잘못된 Base64 형식 카운터 증가
                meterRegistry.counter("logpulse.compression.errors", "type", "invalid_base64").increment();
                log.debug("Content is not Base64 encoded, returning as is");
                return compressedContent;
            } catch (IOException e) {
                // 압축 해제 실패 카운터 증가
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

    public double getCompressionRatio(String originalContent, String compressedContent) {
        if (originalContent == null || compressedContent == null ||
                originalContent.isEmpty() || compressedContent.isEmpty()) {
            return 1.0;
        }

        if (!isCompressed(compressedContent)) {
            return 1.0;
        }

        try {
            byte[] decodedContent = Base64.getDecoder().decode(compressedContent);
            return (double) decodedContent.length / originalContent.getBytes(StandardCharsets.UTF_8).length;
        } catch (IllegalArgumentException e) {
            return 1.0;
        }
    }

    /**
     * 주기적으로 압축 통계 로깅 (1분마다)
     */
    @Scheduled(fixedRate = 60000)
    public void logCompressionStats() {
        double enabled = compressionEnabledCounter.count();
        double disabled = compressionDisabledCounter.count();
        double total = enabled + disabled;

        if (total > 0) {
            double compressionRate = (enabled / total) * 100.0;
            double avgRatio = compressionRatioSummary.mean();
            double p50Ratio = compressionRatioSummary.percentile(0.5);
            double p95Ratio = compressionRatioSummary.percentile(0.95);

            double compressionAvgTimeMs = compressionTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS);
            double decompressionAvgTimeMs = decompressionTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS);

            log.info("Compression stats - Total: {}, Compressed: {} ({:.2f}%), Ratio: avg={:.2f}%, p50={:.2f}%, p95={:.2f}%, " +
                            "Time: compress={:.2f}ms, decompress={:.2f}ms",
                    (long)total, (long)enabled, compressionRate,
                    avgRatio * 100, p50Ratio * 100, p95Ratio * 100,
                    compressionAvgTimeMs, decompressionAvgTimeMs);
        }
    }
}