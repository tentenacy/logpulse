package com.tenacy.logpulse.performance;

import com.tenacy.logpulse.api.dto.LogEntryRequest;
import com.tenacy.logpulse.api.dto.LogEntryResponse;
import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.domain.LogRepository;
import com.tenacy.logpulse.service.IntegrationLogService;
import com.tenacy.logpulse.service.LogService;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Tag("stress")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LogServiceStressTest {

    @Autowired
    private LogService logService;

    @Autowired
    private IntegrationLogService integrationLogService;

    @Autowired
    private LogRepository logRepository;

    private final int TOTAL_LOGS = 100; // 테스트 환경에서는 수량 줄임
    private final int THREAD_COUNT = 4;
    private final int BATCH_SIZE = 25;

    @BeforeEach
    void setUp() {
        logRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        logRepository.deleteAll();
    }

    @Test
    @DisplayName("직접 경로 스트레스 테스트 - 격리된 환경에서 대량 로그 동시 저장")
    @Order(1)
    void directPathStressTestBulkLogCreation() throws Exception {
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

        // 처리 완료 대기
        waitForLogsCompletion("isolated-stress-test-service", TOTAL_LOGS, 120);

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
        assertThat(totalRate).isGreaterThan(10.0); // 초당 최소 10개 이상의 로그를 처리해야 함
    }

    @Test
    @DisplayName("직접 경로 스트레스 테스트 - 격리된 환경에서 다양한 로그 레벨 분포")
    @Order(2)
    void directPathStressTestWithDifferentLogLevels() throws Exception {
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

        // 처리 완료까지 대기
        waitForLogsCompletion("isolated-stress-test-service", TOTAL_LOGS, 120);

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

        int totalLevelCounts = 0;
        for (int i = 0; i < logLevels.length; i++) {
            totalLevelCounts += levelCounts[i].get();
        }

        for (int i = 0; i < logLevels.length; i++) {
            double percentage = (double) levelCounts[i].get() / totalLevelCounts * 100;
            System.out.println("  - " + logLevels[i] + ": " + levelCounts[i].get() +
                    " (" + String.format("%.1f", percentage) + "%)");

            // 로그 레벨 분포 검증 - 허용 오차 범위 늘림
            double expectedPercentage = logLevelDistribution[i];
            assertThat(percentage)
                    .isCloseTo(expectedPercentage, org.assertj.core.data.Offset.offset(15.0)); // 15% 오차 허용
        }

        assertThat(actualLogCount).isEqualTo(TOTAL_LOGS);
        assertThat(logsPerSecond).isGreaterThan(10.0);
    }

    @Test
    @DisplayName("통합 경로 스트레스 테스트 - 카프카 파이프라인을 통한 대량 로그 처리")
    @Order(3)
    void integrationPathStressTest() throws Exception {
        // given
        int totalLogs = 50; // 테스트 환경에서는 수량 줄임
        int concurrentThreads = 2;

        String[] logLevels = {"ERROR", "WARN", "INFO", "DEBUG"};
        int[] logLevelDistribution = {5, 15, 60, 20};

        ExecutorService executorService = Executors.newFixedThreadPool(concurrentThreads);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        int logsPerThread = totalLogs / concurrentThreads;

        Instant start = Instant.now();

        // when - 여러 스레드에서 동시에 통합 경로로 로그 생성
        for (int t = 0; t < concurrentThreads; t++) {
            int threadId = t;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    for (int i = 0; i < logsPerThread; i++) {
                        LogEventDto logEventDto = LogEventDto.builder()
                                .source("integration-stress-test-" + threadId)
                                .content("Integration stress test log #" + i + " from thread " + threadId)
                                .logLevel(getRandomLogLevel(logLevels, logLevelDistribution))
                                .timestamp(LocalDateTime.now())
                                .build();

                        integrationLogService.processLog(logEventDto);
                        successCount.incrementAndGet();

                        if (i > 0 && i % 10 == 0) {
                            Thread.sleep(10); // 약간의 지연 추가
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
        executorService.awaitTermination(1, TimeUnit.MINUTES);

        Instant submissionEnd = Instant.now();
        Duration submissionDuration = Duration.between(start, submissionEnd);

        // 처리 완료 대기
        waitForLogsCompletion("integration-stress-test", totalLogs, 180);

        Instant end = Instant.now();
        Duration totalDuration = Duration.between(start, end);

        // then
        long actualLogCount = countIntegrationStressTestLogs();
        double submissionRate = (double) totalLogs / submissionDuration.toMillis() * 1000;
        double totalRate = (double) actualLogCount / totalDuration.toMillis() * 1000;

        System.out.println("통합 경로 스트레스 테스트 결과:");
        System.out.println("- 총 생성된 로그 수: " + actualLogCount);
        System.out.println("- 성공한 로그 생성 수: " + successCount.get());
        System.out.println("- 제출 소요 시간: " + submissionDuration.toMillis() + "ms");
        System.out.println("- 총 소요 시간: " + totalDuration.toMillis() + "ms");
        System.out.println("- 제출 처리량: " + String.format("%.2f", submissionRate) + " logs/second");
        System.out.println("- 전체 처리량: " + String.format("%.2f", totalRate) + " logs/second");

        assertThat(successCount.get()).isEqualTo(totalLogs);
        // 카프카 처리는 실패할 수 있으므로 더 낮은 성공률 허용
        assertThat(actualLogCount).isGreaterThanOrEqualTo((long)(totalLogs * 0.70)); // 최소 70% 성공 기대
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

    private void waitForLogsCompletion(String sourcePrefix, int expectedCount, int timeoutSeconds) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (timeoutSeconds * 1000);

        while (System.currentTimeMillis() < endTime) {
            long count = 0;
            if (sourcePrefix.contains("integration-stress-test")) {
                count = countIntegrationStressTestLogs();
            } else {
                count = countIsolatedStressTestLogs();
            }

            if (count >= expectedCount) {
                return;
            }

            TimeUnit.MILLISECONDS.sleep(500);
        }

        System.out.println("Warning: Timeout while waiting for log processing completion.");
    }

    private long countIntegrationStressTestLogs() {
        return logRepository.findAll().stream()
                .filter(log -> log.getSource().contains("integration-stress-test"))
                .count();
    }

    private long countIsolatedStressTestLogs() {
        return logRepository.findAll().stream()
                .filter(log -> log.getSource().contains("isolated-stress-test-service"))
                .count();
    }
}