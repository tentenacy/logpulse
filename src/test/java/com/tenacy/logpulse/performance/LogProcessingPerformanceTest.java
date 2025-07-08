package com.tenacy.logpulse.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.api.dto.PerformanceTestRequest;
import com.tenacy.logpulse.service.IntegrationLogService;
import com.tenacy.logpulse.util.SimpleTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LogPulse 성능 테스트
 *
 * 성능 벤치마크:
 * - API 직접 테스트: ~100K logs/sec (비동기 제출)
 * - 단위 테스트: ~130 logs/sec (종단간 완료)
 *
 * 참고: 단위 테스트는 DB 저장 완료까지 측정하므로 처리량이 낮게 나옴
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("performance")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LogProcessingPerformanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IntegrationLogService integrationLogService;

    @Autowired
    private SimpleTestSupport simpleTestSupport;

    @BeforeEach
    void setUp() {
        simpleTestSupport.basicCleanup();
    }

    @AfterEach
    void tearDown() {
        simpleTestSupport.simpleTestIsolation();
    }

    @ParameterizedTest
    @CsvSource({
            "100, 5, 20",    // 적은 로그, 적은 스레드
            "500, 8, 50",    // 중간 크기, 중간 스레드
            "1000, 10, 100"  // 많은 로그, 많은 스레드
    })
    @DisplayName("성능 테스트 API 테스트 - 개선된 버전")
    @Order(1)
    void improvedPerformanceTestApiTest(int totalLogs, int concurrentThreads, int batchSize) throws Exception {
        // given
        PerformanceTestRequest request = PerformanceTestRequest.builder()
                .totalLogs(totalLogs)
                .concurrentThreads(concurrentThreads)
                .batchSize(batchSize)
                .build();

        String requestJson = objectMapper.writeValueAsString(request);

        // when
        MvcResult result = mockMvc.perform(post("/api/v1/performance/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLogs").value(totalLogs))
                .andExpect(jsonPath("$.elapsedTimeMs").isNumber())
                .andExpect(jsonPath("$.logsPerSecond").isNumber())
                .andReturn();

        // 비동기 처리 완료 대기
        simpleTestSupport.waitForProcessingComplete(totalLogs, Duration.ofMinutes(2));

        // then
        String responseJson = result.getResponse().getContentAsString();
        System.out.println("Performance test results: " + responseJson);
    }

    /**
     * 실제 운영 성능: API 테스트 참조 (~100K logs/sec)
     * 이 테스트 결과: 종단간 안정성 검증 (~130 logs/sec)
     */
    @Test
    @DisplayName("직접 성능 테스트 - 격리된 환경에서 대규모 로그 처리")
    @Order(2)
    void isolatedDirectPerformanceTest() throws Exception {
        // 테스트 파라미터
        int totalLogs = 2000;
        int concurrentThreads = 8;
        int batchSize = 100;

        // 로그 레벨 분포 (ERROR: 5%, WARN: 15%, INFO: 60%, DEBUG: 20%)
        String[] logLevels = {"ERROR", "WARN", "INFO", "DEBUG"};
        int[] logLevelDistribution = {5, 15, 60, 20};

        System.out.println("Starting isolated performance test with " + totalLogs +
                " logs, " + concurrentThreads + " threads, batch size " + batchSize);

        long startTime = System.currentTimeMillis();

        // 스레드 풀 생성
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentThreads);

        // 각 스레드가 처리할 로그 수 계산 (균등 분배)
        int logsPerThread = totalLogs / concurrentThreads;
        int remainingLogs = totalLogs % concurrentThreads;

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int t = 0; t < concurrentThreads; t++) {
            // 마지막 스레드가 남은 로그를 처리하도록 함
            int threadLogsCount = logsPerThread + (t < remainingLogs ? 1 : 0);
            int threadIndex = t;

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    for (int i = 0; i < threadLogsCount; i++) {
                        String logLevel = getRandomLogLevel(logLevels, logLevelDistribution);

                        LogEventDto logEventDto = LogEventDto.builder()
                                .source("isolated-performance-test")
                                .content("Isolated performance test log #" + UUID.randomUUID() +
                                        " from thread " + threadIndex)
                                .logLevel(logLevel)
                                .timestamp(LocalDateTime.now())
                                .build();

                        integrationLogService.processLog(logEventDto);
                    }

                    System.out.println("Thread " + threadIndex + " completed: processed " +
                            threadLogsCount + " logs");

                } catch (Exception e) {
                    System.err.println("Error in thread " + threadIndex + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }, executorService);

            futures.add(future);
        }

        // 모든 작업 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 스레드 풀 종료
        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.MINUTES);

        long submissionTime = System.currentTimeMillis();
        System.out.println("All logs submitted in: " + (submissionTime - startTime) + " ms");

        // 비동기 처리 완료까지 대기
        simpleTestSupport.waitForProcessingComplete(totalLogs, Duration.ofMinutes(3));

        long endTime = System.currentTimeMillis();
        long totalElapsedTime = endTime - startTime;
        double logsPerSecond = (double) totalLogs / (totalElapsedTime / 1000.0);

        System.out.println("Isolated performance test completed:");
        System.out.println("Total logs: " + totalLogs);
        System.out.println("Submission time: " + (submissionTime - startTime) + " ms");
        System.out.println("Total elapsed time: " + totalElapsedTime + " ms");
        System.out.println("Throughput: " + String.format("%.2f", logsPerSecond) + " logs/second");

        assertThat(logsPerSecond).isGreaterThan(50); // 초당 최소 50개 이상 처리되어야 함
    }

    @Test
    @DisplayName("연속 성능 테스트 - 테스트 간 격리 검증")
    @Order(3)
    void consecutivePerformanceTest() throws Exception {
        int[] testSizes = {200, 300, 250}; // 서로 다른 크기로 테스트

        for (int i = 0; i < testSizes.length; i++) {
            final int testIndex = i + 1; // final 변수로 선언
            final int currentTestSize = testSizes[i];
            System.out.println("\n=== Consecutive Test " + testIndex + " - " + currentTestSize + " logs ===");

            long startTime = System.currentTimeMillis();

            // 로그 생성 및 전송
            for (int j = 0; j < currentTestSize; j++) {
                LogEventDto logEventDto = LogEventDto.builder()
                        .source("consecutive-test-" + testIndex)
                        .content("Consecutive test log #" + j + " in test " + testIndex)
                        .logLevel("INFO")
                        .timestamp(LocalDateTime.now())
                        .build();

                integrationLogService.processLog(logEventDto);
            }

            // 처리 완료 대기
            simpleTestSupport.waitForProcessingComplete(currentTestSize, Duration.ofMinutes(2));

            long endTime = System.currentTimeMillis();
            double logsPerSecond = (double) currentTestSize / ((endTime - startTime) / 1000.0);

            System.out.println("Test " + testIndex + " completed:");
            System.out.println("- Logs: " + currentTestSize);
            System.out.println("- Time: " + (endTime - startTime) + " ms");
            System.out.println("- Throughput: " + String.format("%.2f", logsPerSecond) + " logs/second");

            // 다음 테스트를 위한 완전한 정리
            if (i < testSizes.length - 1) {
                simpleTestSupport.simpleTestIsolation();
            }
        }
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