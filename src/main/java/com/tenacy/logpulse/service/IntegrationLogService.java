package com.tenacy.logpulse.service;

import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.integration.LogGateway;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationLogService {

    private final LogGateway logGateway;
    private final Counter integrationReceivedCounter;
    private final Counter integrationErrorCounter;
    private final Counter integrationRetryCounter;
    private final MeterRegistry meterRegistry;

    public void processLog(LogEventDto logEventDto) {
        // 수신 카운터 증가
        integrationReceivedCounter.increment();

        // 로그 소스별 카운터 증가
        String source = logEventDto.getSource() != null ? logEventDto.getSource() : "unknown";
        meterRegistry.counter("logpulse.integration.source", "source", source).increment();

        // 로그 레벨별 카운터 증가
        String logLevel = logEventDto.getLogLevel() != null ? logEventDto.getLogLevel().toUpperCase() : "UNKNOWN";
        meterRegistry.counter("logpulse.integration.level", "level", logLevel).increment();

        // 타이머 시작
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            if (logEventDto.getTimestamp() == null) {
                logEventDto.setTimestamp(LocalDateTime.now());
            }

            validateLogEvent(logEventDto);

            log.debug("Submitting log event to integration pipeline: {}", logEventDto);

            // 로그 게이트웨이를 통해 통합 흐름으로 전송
            logGateway.processLog(logEventDto);

            // 성공 타이머 기록
            sample.stop(meterRegistry.timer("logpulse.integration.processing.time", "status", "success"));

        } catch (Exception e) {
            // 오류 카운터 증가
            integrationErrorCounter.increment();

            // 오류 유형별 카운터 증가
            String errorType = e.getClass().getSimpleName();
            meterRegistry.counter("logpulse.integration.error.type", "type", errorType).increment();

            log.error("Error processing log event: {}", e.getMessage(), e);

            // 오류 타이머 기록
            sample.stop(meterRegistry.timer("logpulse.integration.processing.time", "status", "error"));

            // 오류 발생 시 재시도 로직 (최대 3번)
            if (shouldRetry(logEventDto)) {
                retryProcessing(logEventDto, 1);
            }
        }
    }

    private void validateLogEvent(LogEventDto logEventDto) {
        // 로그 레벨 확인 및 기본값 설정
        if (logEventDto.getLogLevel() == null || logEventDto.getLogLevel().trim().isEmpty()) {
            logEventDto.setLogLevel("INFO");
            meterRegistry.counter("logpulse.integration.validation", "field", "logLevel").increment();
        }

        // 소스 확인 및 기본값 설정
        if (logEventDto.getSource() == null || logEventDto.getSource().trim().isEmpty()) {
            logEventDto.setSource("unknown");
            meterRegistry.counter("logpulse.integration.validation", "field", "source").increment();
        }

        // 내용 확인 및 기본값 설정
        if (logEventDto.getContent() == null) {
            logEventDto.setContent("");
            meterRegistry.counter("logpulse.integration.validation", "field", "content").increment();
        }
    }

    private boolean shouldRetry(LogEventDto logEventDto) {
        boolean shouldRetry = "performance-test".equals(logEventDto.getSource());

        // 재시도 결정 카운터
        meterRegistry.counter("logpulse.integration.retry.decision", "retry", String.valueOf(shouldRetry)).increment();

        return shouldRetry;
    }

    private void retryProcessing(LogEventDto logEventDto, int attempt) {
        if (attempt > 3) {
            log.error("Gave up after 3 retry attempts for log event: {}", logEventDto);
            meterRegistry.counter("logpulse.integration.retry.giveup").increment();
            return;
        }

        try {
            // 재시도 카운터 증가
            integrationRetryCounter.increment();

            // 재시도 시도 횟수별 카운터
            meterRegistry.counter("logpulse.integration.retry.attempt", "attempt", String.valueOf(attempt)).increment();

            // 재시도 간격 설정 (지수 백오프)
            long backoffMs = 100 * (long)Math.pow(2, attempt - 1);
            Thread.sleep(backoffMs);

            log.info("Retry attempt {} for log event: {}", attempt, logEventDto);

            // 재시도 처리 시간 측정
            Timer.Sample retrySample = Timer.start(meterRegistry);

            // 재시도 실행
            logGateway.processLog(logEventDto);

            // 재시도 성공 타이머 기록
            retrySample.stop(meterRegistry.timer("logpulse.integration.retry.time", "attempt", String.valueOf(attempt), "status", "success"));

            // 재시도 성공 카운터
            meterRegistry.counter("logpulse.integration.retry.result", "result", "success").increment();

        } catch (Exception e) {
            log.error("Retry attempt {} failed: {}", attempt, e.getMessage());

            // 재시도 실패 카운터
            meterRegistry.counter("logpulse.integration.retry.result", "result", "failure").increment();

            // 다음 재시도 실행
            retryProcessing(logEventDto, attempt + 1);
        }
    }

    @Scheduled(fixedRate = 60000)
    public void logStats() {
        double received = integrationReceivedCounter.count();
        double errors = integrationErrorCounter.count();
        double retries = integrationRetryCounter.count();

        if (received > 0) {
            double errorRate = (errors / received) * 100.0;
            double retryRate = (retries / received) * 100.0;

            log.info("Integration stats - Received: {}, Errors: {} ({:.2f}%), Retries: {} ({:.2f}%)",
                    (long)received, (long)errors, errorRate, (long)retries, retryRate);

            // 소스별 통계 로깅 (상위 5개 소스만)
            meterRegistry.find("logpulse.integration.source").counters().stream()
                    .sorted((a, b) -> Double.compare(b.count(), a.count()))
                    .limit(5)
                    .forEach(counter -> {
                        String source = counter.getId().getTag("source");
                        log.info("  Source {}: {} logs", source, (long)counter.count());
                    });
        }
    }
}