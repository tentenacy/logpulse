package com.tenacy.logpulse.config;

import com.tenacy.logpulse.service.SystemMetricsService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class MetricsInitConfig {

    private final SystemMetricsService systemMetricsService;

    @PostConstruct
    public void init() {
        try {
            log.info("시스템 메트릭 서비스 초기화 시작");
            systemMetricsService.loadInitialMetrics();
            log.info("시스템 메트릭 서비스 초기화 완료");
        } catch (Exception e) {
            log.error("시스템 메트릭 서비스 초기화 실패", e);
        }
    }
}