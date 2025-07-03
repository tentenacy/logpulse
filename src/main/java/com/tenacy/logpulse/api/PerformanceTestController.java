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

        int logsPerThread = totalLogs / concurrentThreads;

        for (int t = 0; t < concurrentThreads; t++) {
            int threadIndex = t;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    for (int b = 0; b < logsPerThread / batchSize; b++) {
                        // 배치 단위로 로그 생성 및 전송
                        for (int i = 0; i < batchSize; i++) {
                            String logLevel = getRandomLogLevel(logLevels, logLevelDistribution);

                            LogEventDto logEventDto = LogEventDto.builder()
                                    .source("performance-test")
                                    .content("Performance test log #" + UUID.randomUUID() +
                                            " from thread " + threadIndex)
                                    .logLevel(logLevel)
                                    .timestamp(LocalDateTime.now())
                                    .build();

                            integrationLogService.processLog(logEventDto);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error in performance test thread {}", threadIndex, e);
                }
            }, executorService);

            futures.add(future);
        }

        // 모든 작업 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

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

        double logsPerSecond = (double) totalLogs / (elapsedTimeMs / 1000.0);

        PerformanceTestResponse response = PerformanceTestResponse.builder()
                .totalLogs(totalLogs)
                .elapsedTimeMs(elapsedTimeMs)
                .logsPerSecond(logsPerSecond)
                .build();

        log.info("Performance test completed: {} logs in {} ms ({} logs/sec)",
                totalLogs, elapsedTimeMs, String.format("%.2f", logsPerSecond));

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