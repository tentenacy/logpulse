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
import java.util.UUID;
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

        log.info("성능 테스트 시작: {} 로그, {} 스레드, 배치 크기 {}",
                totalLogs, concurrentThreads, batchSize);

        // 시작 시간 기록
        long startTime = System.currentTimeMillis();

        // 스레드 풀 생성
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentThreads);

        // 로그 레벨 분포 (ERROR: 5%, WARN: 15%, INFO: 60%, DEBUG: 20%)
        String[] logLevels = {"ERROR", "WARN", "INFO", "DEBUG"};
        int[] logLevelDistribution = {5, 15, 60, 20};

        // 각 스레드가 처리할 로그 수 계산 (균등 분배)
        int logsPerThread = totalLogs / concurrentThreads;
        int remainingLogs = totalLogs % concurrentThreads;

        // 각 스레드에 작업 할당
        for (int t = 0; t < concurrentThreads; t++) {
            // 마지막 스레드가 남은 로그를 처리하도록 함
            int threadLogsCount = logsPerThread + (t < remainingLogs ? 1 : 0);
            int threadIndex = t;

            executorService.submit(() -> {
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
                                    .content("성능 테스트 로그 #" + UUID.randomUUID() +
                                            " (스레드 " + threadIndex + ")")
                                    .logLevel(logLevel)
                                    .timestamp(LocalDateTime.now())
                                    .build();

                            integrationLogService.processLog(logEventDto);
                            logsCreated++;
                        }
                    }

                    log.info("스레드 {} 완료: {}개 로그 생성",
                            threadIndex, logsCreated);

                } catch (Exception e) {
                    log.error("성능 테스트 스레드 {} 오류", threadIndex, e);
                }
            });
        }

        // 스레드 풀 종료 및 대기
        executorService.shutdown();
        try {
            executorService.awaitTermination(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            log.error("성능 테스트 중단됨", e);
            Thread.currentThread().interrupt();
        }

        // 완료 시간 계산
        long endTime = System.currentTimeMillis();
        long elapsedTimeMs = endTime - startTime;
        double logsPerSecond = totalLogs * 1000.0 / elapsedTimeMs;

        log.info("성능 테스트 완료: {}개 로그, {}ms 소요 (초당 {:.2f}개 로그)",
                totalLogs, elapsedTimeMs, logsPerSecond);

        PerformanceTestResponse response = PerformanceTestResponse.builder()
                .totalLogs(totalLogs)
                .elapsedTimeMs(elapsedTimeMs)
                .logsPerSecond(logsPerSecond)
                .build();

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