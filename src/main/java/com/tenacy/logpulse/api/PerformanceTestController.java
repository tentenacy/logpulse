package com.tenacy.logpulse.api;

import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.api.dto.PerformanceTestRequest;
import com.tenacy.logpulse.api.dto.PerformanceTestResponse;
import com.tenacy.logpulse.service.IntegrationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/v1/performance")
@RequiredArgsConstructor
@Slf4j
public class PerformanceTestController {

    private final IntegrationLogService integrationLogService;

    @PostMapping("/test")
    public ResponseEntity<PerformanceTestResponse> runPerformanceTest(
            @RequestBody PerformanceTestRequest request) {

        int totalLogs = request.getTotalLogs();
        int concurrentThreads = request.getConcurrentThreads();
        int batchSize = request.getBatchSize();

        log.info("Starting performance test: {} logs, {} threads, batch size {}",
                totalLogs, concurrentThreads, batchSize);

        long startTime = System.currentTimeMillis();

        // 스레드 풀 생성
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentThreads);

        // 로그 레벨 분포 (ERROR: 5%, WARN: 15%, INFO: 60%, DEBUG: 20%)
        String[] logLevels = {"ERROR", "WARN", "INFO", "DEBUG"};
        int[] logLevelDistribution = {5, 15, 60, 20};

        // 작업 목록 생성
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // 각 스레드가 처리할 로그 수 계산 (균등 분배)
        int logsPerThread = totalLogs / concurrentThreads;
        int remainingLogs = totalLogs % concurrentThreads;

        // 실제 생성될 로그 수 추적
        AtomicInteger actualLogCount = new AtomicInteger(0);

        for (int t = 0; t < concurrentThreads; t++) {
            // 마지막 스레드가 남은 로그를 처리하도록 함
            int threadLogsCount = logsPerThread + (t < remainingLogs ? 1 : 0);
            int threadIndex = t;

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    int logsCreated = 0;
                    while (logsCreated < threadLogsCount) {
                        // 남은 로그 수에 맞게 배치 크기 조정
                        int currentBatchSize = Math.min(batchSize, threadLogsCount - logsCreated);

                        // 배치 단위로 로그 생성 및 전송
                        for (int i = 0; i < currentBatchSize; i++) {
                            String logLevel = getRandomLogLevel(logLevels, logLevelDistribution);

                            LogEventDto logEventDto = LogEventDto.builder()
                                    .source("performance-test")
                                    .content("Performance test log #" + UUID.randomUUID() +
                                            " from thread " + threadIndex)
                                    .logLevel(logLevel)
                                    .timestamp(LocalDateTime.now())
                                    .build();

                            integrationLogService.processLog(logEventDto);
                            logsCreated++;
                            actualLogCount.incrementAndGet();
                        }
                    }

                    log.info("Thread {} completed: created {} logs",
                            threadIndex, logsCreated);

                } catch (Exception e) {
                    log.error("Error in performance test thread {}", threadIndex, e);
                }
            }, executorService);

            futures.add(future);
        }

        // 모든 작업 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        int totalCreatedLogs = actualLogCount.get();
        log.info("Total logs created: {}", totalCreatedLogs);

        // 스레드 풀 종료
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(1, TimeUnit.MINUTES)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }

        long endTime = System.currentTimeMillis();
        long elapsedTimeMs = endTime - startTime;

        double logsPerSecond = (double) totalCreatedLogs / (elapsedTimeMs / 1000.0);

        PerformanceTestResponse response = PerformanceTestResponse.builder()
                .totalLogs(totalCreatedLogs)  // 실제 생성된 로그 수 반환
                .elapsedTimeMs(elapsedTimeMs)
                .logsPerSecond(logsPerSecond)
                .build();

        log.info("Performance test completed: {} logs in {} ms ({} logs/sec)",
                totalCreatedLogs, elapsedTimeMs, String.format("%.2f", logsPerSecond));

        return ResponseEntity.ok(response);
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