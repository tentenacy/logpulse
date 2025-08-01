package com.tenacy.logpulse.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter processedLogsCounter(MeterRegistry registry) {
        return Counter.builder("logpulse.logs.processed")
                .description("처리된 로그 수")
                .register(registry);
    }

    @Bean
    public Counter errorLogsCounter(MeterRegistry registry) {
        return Counter.builder("logpulse.logs.error")
                .description("에러 로그 수")
                .register(registry);
    }

    @Bean
    public Counter warnLogsCounter(MeterRegistry registry) {
        return Counter.builder("logpulse.logs.warn")
                .description("경고 로그 수")
                .register(registry);
    }

    @Bean
    public Counter infoLogsCounter(MeterRegistry registry) {
        return Counter.builder("logpulse.logs.info")
                .description("정보 로그 수")
                .register(registry);
    }

    @Bean
    public Counter debugLogsCounter(MeterRegistry registry) {
        return Counter.builder("logpulse.logs.debug")
                .description("디버그 로그 수")
                .register(registry);
    }
}