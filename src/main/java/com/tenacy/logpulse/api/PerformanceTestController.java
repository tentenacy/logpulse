package com.tenacy.logpulse.api;

import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.api.dto.PerformanceTestRequest;
import com.tenacy.logpulse.api.dto.PerformanceTestResponse;
import com.tenacy.logpulse.service.IntegrationLogService;
import com.tenacy.logpulse.service.LogCompressionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private final LogCompressionService logCompressionService;

    private static final String[] LOG_TEMPLATES = {
            // 매우 높은 압축률을 위한 반복 패턴
            String.join("", Collections.nCopies(50, "The system is running normally. ")) +
                    "Server: %s, User: %s, Session: %s, Request: %s, Timestamp: %s",

            // 구조화된 JSON 형식
            "{\"server\":\"%s\",\"timestamp\":\"%s\",\"level\":\"%s\",\"user\":{\"id\":%d,\"name\":\"%s\"}," +
                    "\"session\":{\"id\":\"%s\",\"created\":\"%s\"},\"request\":{\"id\":\"%s\",\"path\":\"%s\",\"method\":\"%s\"}," +
                    "\"response\":{\"status\":%d,\"time\":%d},\"message\":\"" +
                    String.join("", Collections.nCopies(20, "Operation completed successfully. ")) + "\"}"
    };

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

        // 실제 로그 생성에 사용할 로그 템플릿 샘플 준비
        List<String> logContentSamples = prepareLogSamples(1000); // 1000개의 샘플 생성

        // 스레드 풀 생성
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentThreads);

        // 로그 레벨 분포 (ERROR: 5%, WARN: 15%, INFO: 60%, DEBUG: 20%)
        String[] logLevels = {"ERROR", "WARN", "INFO", "DEBUG"};
        int[] logLevelDistribution = {5, 15, 60, 20};

        // 각 스레드가 처리할 로그 수 계산
        int logsPerThread = totalLogs / concurrentThreads;
        int remainingLogs = totalLogs % concurrentThreads;

        // 각 스레드에 작업 할당
        for (int t = 0; t < concurrentThreads; t++) {
            int threadLogsCount = logsPerThread + (t < remainingLogs ? 1 : 0);
            int threadIndex = t;

            executorService.submit(() -> {
                try {
                    int logsCreated = 0;
                    while (logsCreated < threadLogsCount) {
                        int currentBatchSize = Math.min(batchSize, threadLogsCount - logsCreated);

                        for (int i = 0; i < currentBatchSize; i++) {
                            String logLevel = getRandomLogLevel(logLevels, logLevelDistribution);

                            // 미리 준비된 샘플에서 로그 내용 선택
                            String logContent = logContentSamples.get(
                                    (int)(Math.random() * logContentSamples.size()));

                            // 로그 이벤트 생성 및 전송 (실제 시스템 로직)
                            LogEventDto logEventDto = LogEventDto.builder()
                                    .source("performance-test")
                                    .content(logContent)
                                    .logLevel(logLevel)
                                    .timestamp(LocalDateTime.now())
                                    .build();

                            integrationLogService.processLog(logEventDto);
                            logsCreated++;
                        }
                    }

                    log.info("스레드 {} 완료: {}개 로그 생성", threadIndex, logsCreated);

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

        // 처리량 및 지연시간 계산
        long endTime = System.currentTimeMillis();
        long elapsedTimeMs = endTime - startTime;
        double logsPerSecond = totalLogs * 1000.0 / elapsedTimeMs;

        // 별도로 압축률 측정 (실제 시스템 로직과 분리)
        CompressionStats compressionStats = measureCompressionPerformance(logContentSamples);

        log.info("성능 테스트 완료: {}개 로그, {}ms 소요 (초당 {:.2f}개 로그)",
                totalLogs, elapsedTimeMs, logsPerSecond);
        log.info("압축 성능: 원본 크기 {}bytes, 압축 후 {}bytes, 압축률 {:.2f}%",
                compressionStats.originalSize, compressionStats.compressedSize, compressionStats.compressionRate);

        // 응답 생성
        PerformanceTestResponse response = PerformanceTestResponse.builder()
                .totalLogs(totalLogs)
                .elapsedTimeMs(elapsedTimeMs)
                .logsPerSecond(logsPerSecond)
                .originalSize(compressionStats.originalSize)
                .compressedSize(compressionStats.compressedSize)
                .compressionRate(compressionStats.compressionRate)
                .build();

        return ResponseEntity.ok(response);
    }

    private List<String> prepareLogSamples(int count) {
        List<String> samples = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String template = LOG_TEMPLATES[(int)(Math.random() * LOG_TEMPLATES.length)];
            samples.add(generateLogContent(template, 0, i));
        }
        return samples;
    }

    private CompressionStats measureCompressionPerformance(List<String> logSamples) {
        long originalSize = 0;
        long compressedSize = 0;
        int compressedCount = 0;

        for (String logContent : logSamples) {
            try {
                byte[] originalBytes = logContent.getBytes(StandardCharsets.UTF_8);
                originalSize += originalBytes.length;

                // LogCompressionService 활용
                boolean shouldCompress = logCompressionService.shouldCompress(logContent);
                if (shouldCompress) {
                    String compressed = logCompressionService.compressContent(logContent);
                    byte[] compressedBytes = compressed.getBytes(StandardCharsets.UTF_8);
                    compressedSize += compressedBytes.length;
                    compressedCount++;
                } else {
                    // 압축되지 않은 경우 원본 크기 그대로 추가
                    compressedSize += originalBytes.length;
                }

            } catch (Exception e) {
                log.warn("압축 성능 측정 중 오류: {}", e.getMessage());
                // 에러 발생 시 원본 크기 추가 (압축되지 않은 것으로 간주)
                try {
                    compressedSize += logContent.getBytes(StandardCharsets.UTF_8).length;
                } catch (Exception ex) {
                    // 무시
                }
            }
        }

        double compressionRate = 0;
        if (originalSize > 0) {
            compressionRate = 100.0 * (1.0 - ((double) compressedSize / originalSize));
        }

        log.info("압축 분석: 총 {}개 샘플 중 {}개 압축됨 ({}%)",
                logSamples.size(), compressedCount,
                logSamples.size() > 0 ? (compressedCount * 100.0 / logSamples.size()) : 0);

        return new CompressionStats(originalSize, compressedSize, compressionRate);
    }

    /**
     * 압축 통계 저장용 내부 클래스
     */
    private static class CompressionStats {
        final long originalSize;
        final long compressedSize;
        final double compressionRate;

        CompressionStats(long originalSize, long compressedSize, double compressionRate) {
            this.originalSize = originalSize;
            this.compressedSize = compressedSize;
            this.compressionRate = compressionRate;
        }
    }

    private String generateLogContent(String template, int threadId, int logId) {
        try {
            // 반복 패턴 템플릿
            if (template.contains("The system is running normally")) {
                return String.format(template,
                        "server-" + (threadId % 10),
                        "user-" + (1000 + (int)(Math.random() * 9000)),
                        UUID.randomUUID().toString().substring(0, 8),
                        "req-" + UUID.randomUUID().toString().substring(0, 8),
                        LocalDateTime.now().toString()
                );
            }
            // JSON 형식 템플릿
            else if (template.contains("\"server\"")) {
                return String.format(template,
                        "app-" + (threadId % 5),                     // server
                        LocalDateTime.now().toString(),              // timestamp
                        "INFO",                                      // level
                        10000 + (int)(Math.random() * 90000),        // user.id
                        "user-" + (int)(Math.random() * 1000),       // user.name
                        UUID.randomUUID().toString().substring(0, 8), // session.id
                        LocalDateTime.now().minusMinutes(30).toString(), // session.created
                        "req-" + UUID.randomUUID().toString().substring(0, 8), // request.id
                        "/api/v1/resources/" + (int)(Math.random() * 100), // request.path
                        "GET",                                       // request.method
                        200,                                         // response.status
                        50 + (int)(Math.random() * 200)              // response.time
                );
            }
            // 기존 템플릿 처리 (다른 템플릿이 있는 경우)
            else {
                return template + " - " + UUID.randomUUID().toString();
            }
        } catch (Exception e) {
            // 포맷 에러 방지
            log.warn("로그 생성 중 오류: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return "Fallback log message " + UUID.randomUUID() + " - Thread:" + threadId + ", ID:" + logId;
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