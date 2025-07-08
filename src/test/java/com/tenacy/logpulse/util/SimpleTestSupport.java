package com.tenacy.logpulse.util;

import com.tenacy.logpulse.domain.LogRepository;
import com.tenacy.logpulse.elasticsearch.repository.LogDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 단순한 테스트 지원 클래스 - 카프카 복잡성 제거
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SimpleTestSupport {

    private final LogRepository logRepository;
    private final LogDocumentRepository logDocumentRepository;

    /**
     * 기본 데이터베이스 정리
     */
    public void basicCleanup() {
        log.info("Starting basic cleanup...");

        try {
            // 데이터베이스 정리
            long beforeCount = logRepository.count();
            logRepository.deleteAll();
            log.debug("Cleaned {} records from database", beforeCount);

            // Elasticsearch 정리 (실패해도 무시)
            try {
                logDocumentRepository.deleteAll();
            } catch (Exception e) {
                log.debug("Elasticsearch cleanup failed: {}", e.getMessage());
            }

            log.info("Basic cleanup completed");

        } catch (Exception e) {
            log.warn("Basic cleanup failed: {}", e.getMessage());
        }
    }

    /**
     * 단순한 처리 완료 대기 (데이터베이스 기반)
     */
    public void waitForProcessingComplete(int expectedCount, Duration timeout) {
        log.info("Waiting for {} logs to be processed...", expectedCount);

        long startTime = System.currentTimeMillis();
        long timeoutMs = timeout.toMillis();
        long lastCount = 0;
        int stableChecks = 0;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                long currentCount = logRepository.count();

                if (currentCount == expectedCount) {
                    if (currentCount == lastCount) {
                        stableChecks++;
                        if (stableChecks >= 3) { // 3번 연속 동일하면 완료
                            log.info("Processing completed: {} logs", currentCount);
                            return;
                        }
                    } else {
                        stableChecks = 0;
                    }
                    lastCount = currentCount;
                }

                Thread.sleep(500); // 500ms 대기

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.debug("Error during wait: {}", e.getMessage());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        long finalCount = logRepository.count();
        log.info("Wait completed. Expected: {}, Final: {}", expectedCount, finalCount);
    }

    /**
     * 테스트 간 간단한 격리
     */
    public void simpleTestIsolation() {
        log.info("Applying simple test isolation...");

        try {
            // 1. 현재 상태 확인
            long currentCount = logRepository.count();
            if (currentCount > 0) {
                log.debug("Waiting for current {} logs to stabilize", currentCount);
                waitForProcessingComplete((int) currentCount, Duration.ofSeconds(10));
            }

            // 2. 기본 정리
            basicCleanup();

            // 3. 짧은 대기
            Thread.sleep(1000);

            log.info("Simple test isolation completed");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Test isolation interrupted");
        } catch (Exception e) {
            log.warn("Test isolation failed: {}", e.getMessage());
        }
    }
}