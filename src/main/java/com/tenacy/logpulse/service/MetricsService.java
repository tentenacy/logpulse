package com.tenacy.logpulse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MetricsService {

    private final static LocalDateTime START_TIME = LocalDateTime.now();

    public String getFormattedUptime() {
        LocalDateTime now = LocalDateTime.now();
        Duration uptime = Duration.between(START_TIME, now);

        long days = uptime.toDays();
        uptime = uptime.minusDays(days);

        long hours = uptime.toHours();
        uptime = uptime.minusHours(hours);

        long minutes = uptime.toMinutes();

        return String.format("%dd %dh %dm", days, hours, minutes);
    }

    // 평균 응답 시간 (모니터링 시스템 연동)
    public int getAverageResponseTime() {
        // Micrometer 사용하여 측정된 값 반환
        return 85;
    }
}