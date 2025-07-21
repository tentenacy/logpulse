package com.tenacy.logpulse.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.api.dto.PerformanceTestRequest;
import com.tenacy.logpulse.domain.LogRepository;
import com.tenacy.logpulse.service.IntegrationLogService;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    private LogRepository logRepository;

    @BeforeEach
    void setUp() {
        logRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        logRepository.deleteAll();
    }

    @ParameterizedTest
    @CsvSource({
            "100, 5, 20",    // 적은 로그, 적은 스레드
            "500, 8, 50",    // 중간 크기, 중간 스레드
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
        waitForLogProcessingCompletion("performance-test", totalLogs, 120);

        // then
        String responseJson = result.getResponse().getContentAsString();
        System.out.println("Performance test results: " + responseJson);
    }

    @Test
    @DisplayName("직접 성능 테스트 - 격리된 환경에서 대규모 로그 처리")
    @Order(2)
    void isolatedDirectPerformanceTest() throws Exception {
        // 테스트 파라미터
        int totalLogs = 200; // 테스트 환경에서는 수량 줄임
        int concurrentThreads = 5;
        int batchSize = 50;

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

        AtomicInteger totalProcessedLogs = new AtomicInteger(0);

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
                        totalProcessedLogs.incrementAndGet();
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
        waitForLogProcessingCompletion("isolated-performance-test", totalLogs, 180);

        long endTime = System.currentTimeMillis();
        long totalElapsedTime = endTime - startTime;
        double logsPerSecond = (double) totalLogs / (totalElapsedTime / 1000.0);

        System.out.println("Isolated performance test completed:");
        System.out.println("Total logs: " + totalLogs);
        System.out.println("Submission time: " + (submissionTime - startTime) + " ms");
        System.out.println("Total elapsed time: " + totalElapsedTime + " ms");
        System.out.println("Throughput: " + String.format("%.2f", logsPerSecond) + " logs/second");

        assertThat(totalProcessedLogs.get()).isEqualTo(totalLogs);
        assertThat(logsPerSecond).isGreaterThan(10); // 초당 최소 10개 이상 처리되어야 함
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

    private void waitForLogProcessingCompletion(String source, int expectedCount, int timeoutSeconds) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (timeoutSeconds * 1000);
        long lastCount = 0;
        int stableCount = 0;

        while (System.currentTimeMillis() < endTime) {
            long currentCount = logRepository.findBySourceContaining(source).size();

            if (currentCount >= expectedCount) {
                if (currentCount == lastCount) {
                    stableCount++;
                    if (stableCount >= 3) {
                        // 3번 연속으로 같은 카운트가 유지되면 완료로 간주
                        return;
                    }
                } else {
                    stableCount = 0;
                }
            }

            lastCount = currentCount;
            TimeUnit.MILLISECONDS.sleep(500);
        }

        System.out.println("Warning: Timeout while waiting for log processing. Expected: " +
                expectedCount + ", Actual: " + lastCount);
    }
}