package com.tenacy.logpulse.service;

import com.tenacy.logpulse.api.dto.LogEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogAlertService {

    private final AlertService alertService;

    @Value("${logpulse.alert.error-threshold:10}")
    private int errorThreshold;

    @Value("${logpulse.alert.error-time-window:60000}")
    private long errorTimeWindow; // 밀리초 단위, 기본 1분

    @Value("${logpulse.alert.cooldown-period:300000}")
    private long alertCooldownPeriod; // 밀리초 단위, 기본 5분

    // 전체 오류 카운팅을 위한 변수
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final AtomicLong lastResetTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong lastAlertTime = new AtomicLong(0);

    public void checkLogForAlert(LogEventDto logEventDto) {
        // ERROR 로그만 체크
        if (!"ERROR".equalsIgnoreCase(logEventDto.getLogLevel())) {
            return;
        }

        // 전체 오류 비율 체크
        checkGlobalErrorRate(logEventDto);
    }

    private void checkGlobalErrorRate(LogEventDto logEventDto) {
        // 시간 윈도우를 체크하여 초기화 필요한지 확인
        long currentTime = System.currentTimeMillis();
        long lastReset = lastResetTime.get();

        if (currentTime - lastReset > errorTimeWindow) {
            // 시간 윈도우가 지나면 카운터 초기화
            errorCount.set(1); // 현재 에러를 카운트
            lastResetTime.set(currentTime);
        } else {
            // 시간 윈도우 내에 에러 카운트 증가
            int count = errorCount.incrementAndGet();

            // 임계치 초과하면 알림 발송
            if (count == errorThreshold) {
                // 쿨다운 기간 체크
                long lastAlert = lastAlertTime.get();
                if (currentTime - lastAlert > alertCooldownPeriod) {
                    String subject = "LogPulse 알림: 높은 오류율 감지";
                    String message = String.format(
                            "경고: %d개의 에러가 %d초 이내에 발생했습니다. 최근 에러 로그: %s - %s",
                            errorThreshold,
                            errorTimeWindow / 1000,
                            logEventDto.getSource(),
                            logEventDto.getContent()
                    );

                    sendAlert(subject, message);
                    lastAlertTime.set(currentTime);

                    log.warn("오류 임계값 도달: {}초 내 {}개 에러",
                            errorTimeWindow / 1000, errorThreshold);
                }
            }
        }
    }

    private void sendAlert(String subject, String message) {
        try {
            alertService.sendAlert(subject, message);
        } catch (Exception e) {
            log.error("알림 전송 실패: {}", e.getMessage(), e);
        }
    }
}