package com.tenacy.logpulse.util;

import com.tenacy.logpulse.domain.LogRepository;
import com.tenacy.logpulse.elasticsearch.repository.LogDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
@Slf4j
public class AsyncTestSupport {

    private final LogRepository logRepository;
    private final LogDocumentRepository logDocumentRepository;
    private final TestKafkaUtils testKafkaUtils;

    @Value("${logpulse.kafka.topics.raw-logs}")
    private String rawLogsTopic;

    @Value("${spring.kafka.consumer.group-id}")
    private String consumerGroupId;

    /**
     * 테스트 전 완전한 환경 정리 - 안전한 방식
     */
    public void cleanupBeforeTest() {
        log.info("Starting safe test cleanup...");

        try {
            // 1. 데이터베이스 정리
            long beforeCount = logRepository.count();
            logRepository.deleteAll();
            log.debug("Cleaned {} records from database", beforeCount);

            // 2. Elasticsearch 정리 (실패해도 계속)
            try {
                long esBeforeCount = logDocumentRepository.count();
                logDocumentRepository.deleteAll();
                log.debug("Cleaned {} records from Elasticsearch", esBeforeCount);
            } catch (Exception e) {
                log.warn("Elasticsearch cleanup failed, but continuing: {}", e.getMessage());
            }

            // 3. Kafka 안전 정리 (실패해도 계속)
            testKafkaUtils.fullCleanup(rawLogsTopic, consumerGroupId);

            // 4. 안정화 대기
            Thread.sleep(1000);

            log.info("Safe test cleanup completed");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Test cleanup interrupted");
        } catch (Exception e) {
            log.warn("Some cleanup operations failed, but continuing: {}", e.getMessage());
            // 테스트 실행을 방해하지 않음
        }
    }

    /**
     * 비동기 처리 완료까지 대기 (로그 수 기반)
     */
    public void waitForAsyncProcessingComplete(int expectedLogCount, Duration timeout) {
        log.info("Waiting for async processing complete. Expected logs: {}", expectedLogCount);

        long startTime = System.currentTimeMillis();
        long timeoutMs = timeout.toMillis();

        AtomicLong lastDbCount = new AtomicLong(0);
        AtomicLong stableCount = new AtomicLong(0);

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                // 1. 카프카 처리 완료 확인
                testKafkaUtils.waitForTopicProcessingComplete(rawLogsTopic, consumerGroupId,
                        Duration.ofSeconds(5));

                // 2. 데이터베이스 저장 확인
                long currentDbCount = logRepository.count();

                // 3. Elasticsearch 저장 확인 (선택적)
                long currentEsCount = 0;
                try {
                    currentEsCount = logDocumentRepository.count();
                } catch (Exception e) {
                    log.debug("Elasticsearch count check failed: {}", e.getMessage());
                }

                log.debug("Processing status - DB: {}, ES: {}, Expected: {}",
                        currentDbCount, currentEsCount, expectedLogCount);

                // 데이터베이스 카운트가 예상과 일치하고 안정적인지 확인
                if (currentDbCount == expectedLogCount) {
                    if (lastDbCount.get() == currentDbCount) {
                        stableCount.incrementAndGet();
                        if (stableCount.get() >= 3) { // 3번 연속 동일하면 완료로 간주
                            log.info("Async processing completed. Final count: {}", currentDbCount);
                            return;
                        }
                    } else {
                        stableCount.set(0);
                    }
                    lastDbCount.set(currentDbCount);
                }

                Thread.sleep(200); // 200ms 대기

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Wait interrupted", e);
            } catch (Exception e) {
                log.debug("Error during wait: {}", e.getMessage());
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Wait interrupted", ie);
                }
            }
        }

        long finalCount = logRepository.count();
        log.warn("Timeout waiting for async processing. Expected: {}, Actual: {}",
                expectedLogCount, finalCount);
    }

    /**
     * 테스트 간 격리를 위한 완전한 정리 및 대기 - 안전한 방식
     */
    public void ensureCleanSlate() {
        log.info("Ensuring clean slate for next test...");

        try {
            // 1. 현재 처리 중인 작업들이 완료될 때까지 대기 (타임아웃 단축)
            long currentCount = logRepository.count();
            if (currentCount > 0) {
                log.debug("Waiting for {} logs to be processed", currentCount);
                waitForAsyncProcessingComplete((int) currentCount, Duration.ofSeconds(15));
            }

            // 2. 안전한 정리
            cleanupBeforeTest();

            // 3. 시스템 안정화 대기 (단축)
            Thread.sleep(1000);

            log.info("Clean slate ensured - ready for next test");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Clean slate preparation interrupted");
        } catch (Exception e) {
            log.warn("Some clean slate operations failed, but continuing: {}", e.getMessage());
        }
    }

    /**
     * 성능 테스트용 정확한 완료 대기
     */
    public void waitForExactProcessingComplete(int expectedCount, Duration maxWait) {
        log.info("Waiting for exact processing complete: {} logs", expectedCount);

        long startTime = System.currentTimeMillis();
        long lastLogTime = startTime;
        long stableCountTime = 0;
        long currentCount = 0;

        while (System.currentTimeMillis() - startTime < maxWait.toMillis()) {
            try {
                long newCount = logRepository.count();

                if (newCount != currentCount) {
                    currentCount = newCount;
                    lastLogTime = System.currentTimeMillis();
                    stableCountTime = 0;
                } else {
                    stableCountTime = System.currentTimeMillis() - lastLogTime;
                }

                // 목표 수량에 도달하고 5초간 안정적이면 완료
                if (currentCount == expectedCount && stableCountTime >= 5000) {
                    log.info("Exact processing completed: {} logs processed", currentCount);
                    return;
                }

                // 더 이상 증가하지 않고 10초가 지나면 완료로 간주
                if (stableCountTime >= 10000) {
                    log.info("Processing stabilized at: {} logs (expected: {})",
                            currentCount, expectedCount);
                    return;
                }

                Thread.sleep(500);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Wait interrupted", e);
            }
        }

        log.warn("Timeout waiting for exact processing. Expected: {}, Final: {}",
                expectedCount, logRepository.count());
    }
}