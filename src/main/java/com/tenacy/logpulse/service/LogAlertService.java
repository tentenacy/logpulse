package com.tenacy.logpulse.service;

import com.tenacy.logpulse.api.dto.LogEventDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class LogAlertService {

    private final AlertService alertService;
    private final MeterRegistry meterRegistry;

    private final Counter alertsTriggeredCounter;
    private final Counter errorLogsCounter;
    private final Counter thresholdReachedCounter;

    private final AtomicInteger currentErrorRateGauge = new AtomicInteger(0);
    private final AtomicLong lastAlertTimeGauge = new AtomicLong(0);
    private final AtomicInteger windowErrorCountGauge = new AtomicInteger(0);

    @Value("${logpulse.alert.error-threshold:10}")
    private int errorThreshold;

    @Value("${logpulse.alert.error-time-window:60000}")
    private long errorTimeWindow; // 밀리초 단위, 기본 1분

    @Value("${logpulse.alert.cooldown-period:300000}")
    private long alertCooldownPeriod; // 밀리초 단위, 기본 5분

    @Value("${logpulse.alert.source-specific-enabled:true}")
    private boolean sourceSpecificAlertsEnabled;

    // 전체 오류 카운팅을 위한 변수
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final AtomicLong lastResetTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong lastAlertTime = new AtomicLong(0);

    // 소스별 오류 카운팅을 위한 맵
    private final Map<String, SourceErrorTracker> sourceErrorTrackers = new ConcurrentHashMap<>();

    public LogAlertService(AlertService alertService, MeterRegistry meterRegistry) {
        this.alertService = alertService;
        this.meterRegistry = meterRegistry;

        // 메트릭 등록
        this.alertsTriggeredCounter = Counter.builder("logpulse.alerts.triggered")
                .description("Number of alerts triggered")
                .register(meterRegistry);

        this.errorLogsCounter = Counter.builder("logpulse.alerts.error.logs")
                .description("Number of error logs processed for alerting")
                .register(meterRegistry);

        this.thresholdReachedCounter = Counter.builder("logpulse.alerts.threshold.reached")
                .description("Number of times error threshold was reached")
                .register(meterRegistry);

        // 게이지 등록
        meterRegistry.gauge("logpulse.alerts.error.rate", currentErrorRateGauge, AtomicInteger::get);
        meterRegistry.gauge("logpulse.alerts.last.time", lastAlertTimeGauge, AtomicLong::get);
        meterRegistry.gauge("logpulse.alerts.window.errors", windowErrorCountGauge, AtomicInteger::get);
    }

    public void checkLogForAlert(LogEventDto logEventDto) {
        // ERROR 로그만 체크
        if (!"ERROR".equalsIgnoreCase(logEventDto.getLogLevel())) {
            return;
        }

        // 오류 카운터 증가
        errorLogsCounter.increment();

        // 소스별 메트릭 추적
        String source = logEventDto.getSource() != null ? logEventDto.getSource() : "unknown";
        meterRegistry.counter("logpulse.alerts.source.errors", "source", source).increment();

        // 전체 오류 비율 체크
        checkGlobalErrorRate(logEventDto);

        // 소스별 오류 비율 체크 (활성화된 경우)
        if (sourceSpecificAlertsEnabled) {
            checkSourceSpecificErrorRate(logEventDto);
        }
    }

    private void checkGlobalErrorRate(LogEventDto logEventDto) {
        // 시간 윈도우를 체크하여 초기화 필요한지 확인
        long currentTime = System.currentTimeMillis();
        long lastReset = lastResetTime.get();

        if (currentTime - lastReset > errorTimeWindow) {
            // 시간 윈도우가 지나면 카운터 초기화
            errorCount.set(1); // 현재 에러를 카운트
            lastResetTime.set(currentTime);

            // 게이지 업데이트
            windowErrorCountGauge.set(1);
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
                    Timer.Sample alertTimer = Timer.start(meterRegistry);

                    String subject = "LogPulse Alert: High Error Rate Detected";
                    String message = String.format(
                            "경고: %d개의 에러가 %d초 이내에 발생했습니다. 최근 에러 로그: %s - %s",
                            errorThreshold,
                            errorTimeWindow / 1000,
                            logEventDto.getSource(),
                            logEventDto.getContent()
                    );

                    sendAlert(subject, message, "global");
                    lastAlertTime.set(currentTime);
                    lastAlertTimeGauge.set(currentTime);

                    alertTimer.stop(meterRegistry.timer("logpulse.alerts.processing.time", "type", "global"));

                    log.warn("Global error threshold reached: {} errors in {} seconds",
                            errorThreshold, errorTimeWindow / 1000);
                } else {
                    // 쿨다운 기간 내에 있어 알림 생략
                    meterRegistry.counter("logpulse.alerts.suppressed", "reason", "cooldown").increment();
                    log.debug("Alert suppressed due to cooldown period. Last alert was {} seconds ago.",
                            (currentTime - lastAlert) / 1000);
                }
            }

            // 현재 오류율 계산 및 게이지 업데이트
            double errorRate = (double) count / (errorTimeWindow / 1000f);
            currentErrorRateGauge.set((int) Math.round(errorRate));
        }
    }

    private void checkSourceSpecificErrorRate(LogEventDto logEventDto) {
        String source = logEventDto.getSource() != null ? logEventDto.getSource() : "unknown";

        // 소스 트래커 가져오거나 생성
        SourceErrorTracker tracker = sourceErrorTrackers.computeIfAbsent(source,
                k -> new SourceErrorTracker(errorThreshold, errorTimeWindow));

        // 오류 발생 여부 체크
        if (tracker.trackError()) {
            // 쿨다운 체크
            long currentTime = System.currentTimeMillis();
            if (currentTime - tracker.getLastAlertTime() > alertCooldownPeriod) {
                Timer.Sample alertTimer = Timer.start(meterRegistry);

                String subject = String.format("LogPulse Alert: High Error Rate in Source '%s'", source);
                String message = String.format(
                        "경고: 소스 '%s'에서 %d개의 에러가 %d초 이내에 발생했습니다. 최근 에러 로그: %s",
                        source,
                        errorThreshold,
                        errorTimeWindow / 1000,
                        logEventDto.getContent()
                );

                sendAlert(subject, message, "source-specific");
                tracker.setLastAlertTime(currentTime);

                alertTimer.stop(meterRegistry.timer("logpulse.alerts.processing.time", "type", "source"));

                log.warn("Source-specific error threshold reached for '{}': {} errors in {} seconds",
                        source, errorThreshold, errorTimeWindow / 1000);
            } else {
                // 쿨다운 기간 내에 있어 알림 생략
                meterRegistry.counter("logpulse.alerts.suppressed", "reason", "source-cooldown").increment();
            }
        }

        // 소스별 현재 오류 수 게이지 업데이트
        meterRegistry.gauge("logpulse.alerts.source." + source + ".count",
                sourceErrorTrackers.getOrDefault(source, new SourceErrorTracker(0, 0)),
                SourceErrorTracker::getErrorCount);
    }

    private void sendAlert(String subject, String message, String type) {
        try {
            alertService.sendAlert(subject, message);
            alertsTriggeredCounter.increment();

            // 알림 유형별 카운터 증가
            meterRegistry.counter("logpulse.alerts.sent", "type", type).increment();

        } catch (Exception e) {
            log.error("Failed to send alert: {}", e.getMessage(), e);

            // 알림 오류 카운터 증가
            meterRegistry.counter("logpulse.alerts.errors").increment();
        }
    }

    @Scheduled(fixedRate = 300000)
    public void logAlertStats() {
        double triggeredAlerts = alertsTriggeredCounter.count();
        double errorLogs = errorLogsCounter.count();

        if (errorLogs > 0) {
            double alertRate = (triggeredAlerts / errorLogs) * 100.0;
            int activeTrackers = sourceErrorTrackers.size();

            log.info("Alert stats - Errors processed: {}, Alerts triggered: {} ({:.2f}%), " +
                            "Active source trackers: {}",
                    (long)errorLogs,
                    (long)triggeredAlerts,
                    alertRate,
                    activeTrackers);

            // 통계 게이지 업데이트
            meterRegistry.gauge("logpulse.alerts.trigger.rate", alertRate);
        }

        // 오래된 소스 트래커 정리
        cleanupOldSourceTrackers();
    }

    private void cleanupOldSourceTrackers() {
        long currentTime = System.currentTimeMillis();
        long cleanupThreshold = currentTime - TimeUnit.HOURS.toMillis(1);

        int beforeSize = sourceErrorTrackers.size();
        sourceErrorTrackers.entrySet().removeIf(entry ->
                entry.getValue().getLastUpdateTime() < cleanupThreshold);
        int afterSize = sourceErrorTrackers.size();

        if (beforeSize > afterSize) {
            log.debug("Cleaned up {} inactive source trackers", beforeSize - afterSize);
        }
    }

    private static class SourceErrorTracker {
        private final int threshold;
        private final long timeWindow;
        private final AtomicInteger errorCount = new AtomicInteger(0);
        private final AtomicLong lastResetTime = new AtomicLong(System.currentTimeMillis());
        @Setter
        @Getter
        private long lastAlertTime = 0;
        @Getter
        private long lastUpdateTime = System.currentTimeMillis();

        public SourceErrorTracker(int threshold, long timeWindow) {
            this.threshold = threshold;
            this.timeWindow = timeWindow;
        }

        public boolean trackError() {
            lastUpdateTime = System.currentTimeMillis();

            // 시간 윈도우 체크
            if (lastUpdateTime - lastResetTime.get() > timeWindow) {
                errorCount.set(1);
                lastResetTime.set(lastUpdateTime);
                return false;
            }

            // 에러 카운트 증가 및 임계값 체크
            return errorCount.incrementAndGet() == threshold;
        }

        public int getErrorCount() {
            return errorCount.get();
        }

    }
}