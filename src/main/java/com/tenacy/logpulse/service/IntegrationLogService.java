package com.tenacy.logpulse.service;

import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.integration.LogGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationLogService {

    private final LogGateway logGateway;
    private final AtomicLong receivedCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    public void processLog(LogEventDto logEventDto) {
        long count = receivedCount.incrementAndGet();

        if (count % 10000 == 0) {
            log.info("IntegrationLogService stats - Received: {}, Errors: {}",
                    receivedCount.get(), errorCount.get());
        }

        try {
            if (logEventDto.getTimestamp() == null) {
                logEventDto.setTimestamp(LocalDateTime.now());
            }

            validateLogEvent(logEventDto);

            log.debug("Submitting log event to integration pipeline: {}", logEventDto);

            // 로그 게이트웨이를 통해 통합 흐름으로 전송
            logGateway.processLog(logEventDto);
        } catch (Exception e) {
            errorCount.incrementAndGet();
            log.error("Error processing log event: {}", e.getMessage(), e);

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
        }

        // 소스 확인 및 기본값 설정
        if (logEventDto.getSource() == null || logEventDto.getSource().trim().isEmpty()) {
            logEventDto.setSource("unknown");
        }

        // 내용 확인 및 기본값 설정
        if (logEventDto.getContent() == null) {
            logEventDto.setContent("");
        }
    }

    /**
     * 재시도 여부 결정 (성능 테스트 관련 로그는 항상 재시도)
     */
    private boolean shouldRetry(LogEventDto logEventDto) {
        return "performance-test".equals(logEventDto.getSource());
    }

    /**
     * 재시도 로직 구현
     */
    private void retryProcessing(LogEventDto logEventDto, int attempt) {
        if (attempt > 3) {
            log.error("Gave up after 3 retry attempts for log event: {}", logEventDto);
            return;
        }

        try {
            // 재시도 간격 설정 (지수 백오프)
            Thread.sleep(100 * (long)Math.pow(2, attempt - 1));

            log.info("Retry attempt {} for log event: {}", attempt, logEventDto);
            logGateway.processLog(logEventDto);
        } catch (Exception e) {
            log.error("Retry attempt {} failed: {}", attempt, e.getMessage());
            retryProcessing(logEventDto, attempt + 1);
        }
    }
}