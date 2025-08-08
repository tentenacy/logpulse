package com.tenacy.logpulse.service;

import com.tenacy.logpulse.domain.LogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class SystemMetricsService {

    private final LogRepository logRepository;
    private final MeterRegistry meterRegistry;

    // 실시간 메트릭 저장을 위한 변수들
    private final AtomicLong processedLogsInLastMinute = new AtomicLong(0);
    private final AtomicLong totalProcessedLogs = new AtomicLong(0);
    private final AtomicLong errorLogsInLastMinute = new AtomicLong(0);
    private final AtomicLong totalResponseTimeMs = new AtomicLong(0);
    private final AtomicInteger responseCount = new AtomicInteger(0);

    // 슬라이딩 윈도우 메트릭 (최근 10분 단위)
    private final long[] processedLogsPerMinute = new long[10];
    private final long[] errorLogsPerMinute = new long[10];
    private final long[] avgResponseTimePerMinute = new long[10];
    private int currentMinuteIndex = 0;

    // 마지막 집계 시간
    private LocalDateTime lastAggregationTime = LocalDateTime.now();

    @Autowired
    public SystemMetricsService(LogRepository logRepository, MeterRegistry meterRegistry) {
        this.logRepository = logRepository;
        this.meterRegistry = meterRegistry;

        // 초기화
        for (int i = 0; i < 10; i++) {
            processedLogsPerMinute[i] = 0;
            errorLogsPerMinute[i] = 0;
            avgResponseTimePerMinute[i] = 0;
        }
    }

    public void recordProcessedLog() {
        processedLogsInLastMinute.incrementAndGet();
        totalProcessedLogs.incrementAndGet();
    }

    public void recordErrorLog() {
        errorLogsInLastMinute.incrementAndGet();
    }

    public void recordResponseTime(long responseTimeMs) {
        totalResponseTimeMs.addAndGet(responseTimeMs);
        responseCount.incrementAndGet();

        // Micrometer 타이머에도 기록
        Timer timer = meterRegistry.timer("logpulse.api.response.time");
        timer.record(responseTimeMs, TimeUnit.MILLISECONDS);
    }

    public long getCurrentProcessedRate() {
        return processedLogsInLastMinute.get();
    }

    public double getCurrentErrorRate() {
        long processed = processedLogsInLastMinute.get();
        if (processed == 0) return 0.0;

        return (double) errorLogsInLastMinute.get() / processed * 100.0;
    }

    public int getCurrentAvgResponseTime() {
        int count = responseCount.get();
        if (count == 0) return 0;

        return (int) (totalResponseTimeMs.get() / count);
    }

    public long getAverageProcessedRate() {
        long sum = 0;
        int count = Math.min(5, 10); // 최근 5분 또는 가용 데이터

        for (int i = 0; i < count; i++) {
            int index = (currentMinuteIndex - i + 10) % 10; // 역순으로 계산
            sum += processedLogsPerMinute[index];
        }

        return count > 0 ? sum / count : 0;
    }

    public double getAverageErrorRate() {
        long totalProcessed = 0;
        long totalErrors = 0;
        int count = Math.min(5, 10); // 최근 5분 또는 가용 데이터

        for (int i = 0; i < count; i++) {
            int index = (currentMinuteIndex - i + 10) % 10;
            totalProcessed += processedLogsPerMinute[index];
            totalErrors += errorLogsPerMinute[index];
        }

        if (totalProcessed == 0) return 0.0;
        return (double) totalErrors / totalProcessed * 100.0;
    }

    public int getAverageResponseTime() {
        long sum = 0;
        int count = 0;

        for (int i = 0; i < 5; i++) {
            int index = (currentMinuteIndex - i + 10) % 10;
            if (avgResponseTimePerMinute[index] > 0) {
                sum += avgResponseTimePerMinute[index];
                count++;
            }
        }

        return count > 0 ? (int)(sum / count) : 0;
    }

    @Scheduled(fixedRate = 60000) // 1분마다 실행
    public void aggregateMinuteMetrics() {
        // 현재 값 기록
        processedLogsPerMinute[currentMinuteIndex] = processedLogsInLastMinute.getAndSet(0);
        errorLogsPerMinute[currentMinuteIndex] = errorLogsInLastMinute.getAndSet(0);

        int count = responseCount.getAndSet(0);
        if (count > 0) {
            avgResponseTimePerMinute[currentMinuteIndex] = totalResponseTimeMs.getAndSet(0) / count;
        } else {
            avgResponseTimePerMinute[currentMinuteIndex] = 0;
        }

        // 인덱스 이동
        currentMinuteIndex = (currentMinuteIndex + 1) % 10;

        // 시간 업데이트
        lastAggregationTime = LocalDateTime.now();

        // 로그
        log.debug("메트릭 집계 완료 - 처리량: {}/분, 오류율: {:.2f}%, 응답시간: {}ms",
                processedLogsPerMinute[(currentMinuteIndex - 1 + 10) % 10],
                getAverageErrorRate(),
                getAverageResponseTime());
    }

    public void loadInitialMetrics() {
        try {
            // 최근 10분 동안의 로그 처리량과 오류율 집계
            LocalDateTime now = LocalDateTime.now();

            for (int i = 0; i < 10; i++) {
                LocalDateTime minuteStart = now.minusMinutes(i + 1);
                LocalDateTime minuteEnd = now.minusMinutes(i);

                long processed = logRepository.countByCreatedAtBetween(minuteStart, minuteEnd);
                long errors = logRepository.countByLogLevelAndCreatedAtBetween("ERROR", minuteStart, minuteEnd);

                int index = (currentMinuteIndex - i - 1 + 10) % 10;
                processedLogsPerMinute[index] = processed;
                errorLogsPerMinute[index] = errors;

                // 응답 시간은 추정치 (실제 로그에 포함되어 있지 않으므로)
                avgResponseTimePerMinute[index] = 50; // 기본값 50ms
            }

            log.info("초기 메트릭 로드 완료 - 최근 평균 처리량: {}/분, 평균 오류율: {:.2f}%",
                    getAverageProcessedRate(), getAverageErrorRate());

        } catch (Exception e) {
            log.error("초기 메트릭 로드 실패", e);
        }
    }
}