package com.tenacy.logpulse.performance;

import com.tenacy.logpulse.api.dto.LogEntryRequest;
import com.tenacy.logpulse.api.dto.LogEntryResponse;
import com.tenacy.logpulse.domain.LogRepository;
import com.tenacy.logpulse.service.LogService;
import com.tenacy.logpulse.util.AsyncTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로그 서비스 스트레스 테스트 - 동시성 및 안정성 검증
 *
 * 참고: 측정되는 처리량은 완전한 DB 저장까지의 시간 (실제 처리량과 다름)
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("stress")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LogServiceStressTest {

    @Autowired
    private LogService logService;

    @Autowired
    private LogRepository logRepository;

    @Autowired
    private AsyncTestSupport asyncTestSupport;

    private final int TOTAL_LOGS = 1000;
    private final int THREAD_COUNT = 8;
    private final int BATCH_SIZE = 50;

    @BeforeEach
    void setUp() {
        asyncTestSupport.cleanupBeforeTest();
    }

    @AfterEach
    void tearDown() {
        asyncTestSupport.ensureCleanSlate();
    }

    @Test
    @DisplayName("로그 서비스 스트레스 테스트 - 격리된 환경에서 대량 로그 동시 저장")
    @Order(1)
    void isolatedStressTestBulkLogCreation() throws Exception {
        // given
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        int logsPerThread = TOTAL_LOGS / THREAD_COUNT;

        Instant start = Instant.now();

        // when - 여러 스레드에서 동시에 로그 생성
        for (int t = 0; t < THREAD_COUNT; t++) {
            int threadId = t;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    for (int i = 0; i < logsPerThread; i++) {
                        LogEntryRequest request = createRandomLogRequest(threadId, i);
                        LogEntryResponse response = logService.createLog(request);

                        if (response != null && response.getId() != null) {
                            successCount.incrementAndGet();
                        }

                        // 배치 단위로 잠시 쉬어줌
                        if (i > 0 && i % BATCH_SIZE == 0) {
                            Thread.sleep(5);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error in thread " + threadId + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }, executorService);

            futures.add(future);
        }

        // 모든 작업 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executorService.shutdown();
        executorService.awaitTermination(2, TimeUnit.MINUTES);

        Instant submissionEnd = Instant.now();
        Duration submissionDuration = Duration.between(start, submissionEnd);

        // 데이터베이스 저장 완료까지 대기
        asyncTestSupport.waitForExactProcessingComplete(TOTAL_LOGS, Duration.ofMinutes(3));

        Instant end = Instant.now();
        Duration totalDuration = Duration.between(start, end);

        // then
        long actualLogCount = logRepository.count();
        double submissionRate = (double) TOTAL_LOGS / submissionDuration.toMillis() * 1000;
        double totalRate = (double) actualLogCount / totalDuration.toMillis() * 1000;

        System.out.println("격리된 스트레스 테스트 결과:");
        System.out.println("- 총 생성된 로그 수: " + actualLogCount);
        System.out.println("- 성공한 로그 생성 수: " + successCount.get());
        System.out.println("- 제출 소요 시간: " + submissionDuration.toMillis() + "ms");
        System.out.println("- 총 소요 시간: " + totalDuration.toMillis() + "ms");
        System.out.println("- 제출 처리량: " + String.format("%.2f", submissionRate) + " logs/second");
        System.out.println("- 전체 처리량: " + String.format("%.2f", totalRate) + " logs/second");

        assertThat(actualLogCount).isEqualTo(TOTAL_LOGS);
        assertThat(successCount.get()).isEqualTo(TOTAL_LOGS);
        assertThat(totalRate).isGreaterThan(30.0); // 초당 최소 30개 이상의 로그를 처리해야 함
    }

    @Test
    @DisplayName("로그 서비스 스트레스 테스트 - 격리된 환경에서 다양한 로그 레벨 분포")
    @Order(2)
    void isolatedStressTestWithDifferentLogLevels() throws Exception {
        // given
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        String[] logLevels = {"ERROR", "WARN", "INFO", "DEBUG"};
        int[] logLevelDistribution = {5, 15, 60, 20}; // 백분율 분포

        AtomicInteger[] levelCounts = new AtomicInteger[logLevels.length];
        for (int i = 0; i < logLevels.length; i++) {
            levelCounts[i] = new AtomicInteger(0);
        }

        int logsPerThread = TOTAL_LOGS / THREAD_COUNT;

        Instant start = Instant.now();

        // when
        for (int t = 0; t < THREAD_COUNT; t++) {
            int threadId = t;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    for (int i = 0; i < logsPerThread; i++) {
                        String logLevel = getRandomLogLevel(logLevels, logLevelDistribution);

                        // 로그 레벨 카운트 증가
                        for (int j = 0; j < logLevels.length; j++) {
                            if (logLevels[j].equals(logLevel)) {
                                levelCounts[j].incrementAndGet();
                                break;
                            }
                        }

                        LogEntryRequest request = LogEntryRequest.builder()
                                .source("isolated-stress-test-service-" + threadId)
                                .content("Isolated stress test log #" + i + " from thread " + threadId +
                                        " with UUID " + UUID.randomUUID())
                                .logLevel(logLevel)
                                .build();

                        logService.createLog(request);

                        if (i > 0 && i % BATCH_SIZE == 0) {
                            Thread.sleep(5);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error in thread " + threadId + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }, executorService);

            futures.add(future);
        }

        // 모든 작업 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executorService.shutdown();
        executorService.awaitTermination(2, TimeUnit.MINUTES);

        // 저장 완료까지 대기
        asyncTestSupport.waitForExactProcessingComplete(TOTAL_LOGS, Duration.ofMinutes(3));

        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        // then
        long actualLogCount = logRepository.count();
        double logsPerSecond = (double) actualLogCount / duration.toMillis() * 1000;

        System.out.println("격리된 로그 레벨 분포 스트레스 테스트 결과:");
        System.out.println("- 총 생성된 로그 수: " + actualLogCount);
        System.out.println("- 소요 시간: " + duration.toMillis() + "ms");
        System.out.println("- 초당 처리량: " + String.format("%.2f", logsPerSecond) + " logs/second");
        System.out.println("- 로그 레벨 분포:");

        for (int i = 0; i < logLevels.length; i++) {
            double percentage = (double) levelCounts[i].get() / actualLogCount * 100;
            System.out.println("  - " + logLevels[i] + ": " + levelCounts[i].get() +
                    " (" + String.format("%.1f", percentage) + "%)");

            // 로그 레벨 분포 검증
            double expectedPercentage = logLevelDistribution[i];
            assertThat(percentage)
                    .isCloseTo(expectedPercentage, org.assertj.core.data.Offset.offset(8.0)); // 8% 오차 허용
        }

        assertThat(actualLogCount).isEqualTo(TOTAL_LOGS);
        assertThat(logsPerSecond).isGreaterThan(25.0);
    }

    @Test
    @DisplayName("연속 스트레스 테스트 - 테스트 간 격리 검증")
    @Order(3)
    void consecutiveStressTest() throws Exception {
        int[] testSizes = {200, 300, 250}; // 서로 다른 크기로 테스트
        String[] testTypes = {"TYPE_A", "TYPE_B", "TYPE_C"};

        for (int testIndex = 0; testIndex < testSizes.length; testIndex++) {
            final int currentTestIndex = testIndex + 1; // final 변수로 선언
            final int currentTestSize = testSizes[testIndex];
            final String testType = testTypes[testIndex];

            System.out.println("\n=== Consecutive Stress Test " + currentTestIndex +
                    " - " + currentTestSize + " logs (" + testType + ") ===");

            ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            int logsPerThread = currentTestSize / THREAD_COUNT;
            int remainingLogs = currentTestSize % THREAD_COUNT;

            Instant start = Instant.now();

            for (int t = 0; t < THREAD_COUNT; t++) {
                final int threadLogsCount = logsPerThread + (t < remainingLogs ? 1 : 0);
                final int threadId = t;

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        for (int i = 0; i < threadLogsCount; i++) {
                            LogEntryRequest request = LogEntryRequest.builder()
                                    .source("consecutive-stress-" + testType + "-" + threadId)
                                    .content("Consecutive stress test log #" + i +
                                            " from thread " + threadId + " test " + currentTestIndex)
                                    .logLevel("INFO")
                                    .build();

                            logService.createLog(request);
                        }
                    } catch (Exception e) {
                        System.err.println("Error in consecutive test " +
                                currentTestIndex + " thread " + threadId + ": " + e.getMessage());
                    }
                }, executorService);

                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            executorService.shutdown();
            executorService.awaitTermination(1, TimeUnit.MINUTES);

            // 처리 완료 대기
            asyncTestSupport.waitForExactProcessingComplete(currentTestSize, Duration.ofMinutes(2));

            Instant end = Instant.now();
            Duration duration = Duration.between(start, end);
            double logsPerSecond = (double) currentTestSize / duration.toMillis() * 1000;

            System.out.println("Test " + currentTestIndex + " completed:");
            System.out.println("- Type: " + testType);
            System.out.println("- Logs: " + currentTestSize);
            System.out.println("- Time: " + duration.toMillis() + " ms");
            System.out.println("- Throughput: " + String.format("%.2f", logsPerSecond) + " logs/second");

            // 다음 테스트를 위한 완전한 정리
            if (testIndex < testSizes.length - 1) {
                asyncTestSupport.ensureCleanSlate();
            }
        }
    }

    private LogEntryRequest createRandomLogRequest(int threadId, int logNumber) {
        String[] logLevels = {"ERROR", "WARN", "INFO", "DEBUG"};
        String logLevel = logLevels[(int) (Math.random() * logLevels.length)];

        return LogEntryRequest.builder()
                .source("isolated-stress-test-service-" + threadId)
                .content("Isolated stress test log #" + logNumber + " from thread " + threadId +
                        " with UUID " + UUID.randomUUID())
                .logLevel(logLevel)
                .build();
    }

    private String getRandomLogLevel(String[] logLevels, int[] distribution) {
        int randomValue = (int) (Math.random() * 100);
        int cumulativeProb = 0;

        for (int i = 0; i < distribution.length; i++) {
            cumulativeProb += distribution[i];
            if (randomValue < cumulativeProb) {
                return logLevels[i];
            }
        }

        return logLevels[2]; // 기본값 INFO
    }
}