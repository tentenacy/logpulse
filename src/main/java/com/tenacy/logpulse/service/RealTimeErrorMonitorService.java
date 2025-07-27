package com.tenacy.logpulse.service;

import com.tenacy.logpulse.api.dto.LogEventDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class RealTimeErrorMonitorService {

    private final AlertService alertService;
    private final MeterRegistry meterRegistry;

    private final Counter errorLogsCounter;
    private final Counter alertsTriggeredCounter;
    private final Counter thresholdReachedCounter;

    private final AtomicInteger currentErrorRateGauge = new AtomicInteger(0);
    private final AtomicLong lastAlertTimeGauge = new AtomicLong(0);
    private final AtomicInteger windowErrorCountGauge = new AtomicInteger(0);

    // 소스별 오류 추적 맵
    private final Map<String, Integer> sourceErrorCounts = new ConcurrentHashMap<>();

    @Value("${logpulse.monitor.error-threshold:10}")
    private int errorThreshold;

    @Value("${logpulse.monitor.error-time-window:60000}")
    private long errorTimeWindow; // 밀리초 단위, 기본 1분

    @Value("${logpulse.monitor.cooldown-period:300000}")
    private long alertCooldownPeriod; // 밀리초 단위, 기본 5분

    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final AtomicLong lastResetTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong lastAlertTime = new AtomicLong(0);

    public RealTimeErrorMonitorService(AlertService alertService, MeterRegistry meterRegistry) {
        this.alertService = alertService;
        this.meterRegistry = meterRegistry;

        // 메트릭 등록
        this.errorLogsCounter = Counter.builder("logpulse.monitor.error.logs")
                .description("Number of error logs processed by real-time monitor")
                .register(meterRegistry);

        this.alertsTriggeredCounter = Counter.builder("logpulse.monitor.alerts.triggered")
                .description("Number of alerts triggered by real-time monitor")
                .register(meterRegistry);

        this.thresholdReachedCounter = Counter.builder("logpulse.monitor.threshold.reached")
                .description("Number of times error threshold was reached")
                .register(meterRegistry);

        // 게이지 등록
        meterRegistry.gauge("logpulse.monitor.error.rate", currentErrorRateGauge);
        meterRegistry.gauge("logpulse.monitor.last.alert.time", lastAlertTimeGauge);
        meterRegistry.gauge("logpulse.monitor.window.errors", windowErrorCountGauge);
    }

    public void monitorLog(LogEventDto logEventDto) {
        // ERROR 로그만 체크
        if (!"ERROR".equalsIgnoreCase(logEventDto.getLogLevel())) {
            return;
        }

        // 오류 카운터 증가
        errorLogsCounter.increment();

        // 소스별 카운터 증가
        String source = logEventDto.getSource() != null ? logEventDto.getSource() : "unknown";
        meterRegistry.counter("logpulse.monitor.source.errors", "source", source).increment();

        // 소스별 오류 수 업데이트
        sourceErrorCounts.compute(source, (k, v) -> v == null ? 1 : v + 1);

        // 시간 윈도우 체크
        Timer.Sample monitorTimer = Timer.start(meterRegistry);
        long currentTime = System.currentTimeMillis();
        long lastReset = lastResetTime.get();

        if (currentTime - lastReset > errorTimeWindow) {
            // 시간 윈도우가 지나면 카운터 초기화
            errorCount.set(1); // 현재 에러를 카운트
            lastResetTime.set(currentTime);

            // 게이지 업데이트
            windowErrorCountGauge.set(1);

            monitorTimer.stop(meterRegistry.timer("logpulse.monitor.processing.time",
                    "action", "reset"));
        } else {
            // 시간 윈도우 내에 에러 카운트 증가
            int count = errorCount.incrementAndGet();
            windowErrorCountGauge.set(count);

            // 임계치 초과하면 알림 발송
            if (count == errorThreshold) {
                thresholdReachedCounter.increment();

                // 쿨다운 기간 체크
                long lastAlert = lastAlertTime.get();
                if (currentTime - lastAlert > alertCooldownPeriod) {
                    String subject = "LogPulse 실시간 알림: 오류 급증 감지";
                    String message = String.format(
                            "경고: %d개의 ERROR 로그가 %d초 이내에 발생했습니다. 최근 오류: %s - %s",
                            errorThreshold,
                            errorTimeWindow / 1000,
                            logEventDto.getSource(),
                            logEventDto.getContent()
                    );

                    sendAlert(subject, message);
                    lastAlertTime.set(currentTime);
                    lastAlertTimeGauge.set(currentTime);

                    monitorTimer.stop(meterRegistry.timer("logpulse.monitor.processing.time",
                            "action", "alert"));

                    log.warn("실시간 오류 임계값 초과: {}초 내 {}개 ERROR 로그",
                            errorTimeWindow / 1000, errorThreshold);
                } else {
                    // 쿨다운 기간 내에 있어 알림 생략
                    meterRegistry.counter("logpulse.monitor.alerts.suppressed").increment();

                    monitorTimer.stop(meterRegistry.timer("logpulse.monitor.processing.time",
                            "action", "suppress"));
                }
            } else {
                monitorTimer.stop(meterRegistry.timer("logpulse.monitor.processing.time",
                        "action", "count"));
            }

            // 현재 오류율 계산 및 게이지 업데이트
            double errorRate = (double) count / (errorTimeWindow / 1000);
            currentErrorRateGauge.set((int) Math.round(errorRate));
        }
    }

    private void sendAlert(String subject, String message) {
        try {
            alertService.sendAlert(subject, message);
            alertsTriggeredCounter.increment();
        } catch (Exception e) {
            log.error("Failed to send alert: {}", e.getMessage(), e);
            meterRegistry.counter("logpulse.monitor.alert.errors").increment();
        }
    }

    @Scheduled(fixedRate = 300000)
    public void logMonitoringStats() {
        double errorLogs = errorLogsCounter.count();
        double alertsTriggered = alertsTriggeredCounter.count();
        double thresholdReached = thresholdReachedCounter.count();

        if (errorLogs > 0) {
            double alertRate = (alertsTriggered / errorLogs) * 100.0;
            double thresholdRate = (thresholdReached / errorLogs) * 100.0;

            log.info("Error monitoring stats - Error logs: {}, Threshold reached: {} ({:.2f}%), " +
                            "Alerts triggered: {} ({:.2f}%), Window count: {}",
                    (long)errorLogs,
                    (long)thresholdReached,
                    thresholdRate,
                    (long)alertsTriggered,
                    alertRate,
                    windowErrorCountGauge.get());

            // 소스별 오류 로깅 (상위 5개)
            sourceErrorCounts.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(5)
                    .forEach(entry -> {
                        String source = entry.getKey();
                        int count = entry.getValue();
                        double sourceRate = (count / errorLogs) * 100.0;
                        log.info("  Source '{}': {} errors ({:.1f}%)",
                                source, count, sourceRate);

                        // 소스별 오류 비율 게이지 업데이트
                        meterRegistry.gauge("logpulse.monitor.source." + source + ".rate", sourceRate);
                    });
        }
    }

    @Scheduled(fixedRate = 3600000)
    public void cleanupOldSourceCounters() {
        int beforeSize = sourceErrorCounts.size();

        // 오래된 소스는 제거하지 않고 모든 소스를 유지하되,
        // 너무 많아지면 가장 적은 오류 수를 가진 항목부터 제거
        if (beforeSize > 100) {
            sourceErrorCounts.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(a.getValue(), b.getValue()))
                    .limit(beforeSize - 50)
                    .forEach(entry -> sourceErrorCounts.remove(entry.getKey()));

            int afterSize = sourceErrorCounts.size();
            log.debug("Cleaned up {} source error counters", beforeSize - afterSize);
        }
    }
}