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
                .description("The number of logs processed")
                .register(registry);
    }

    @Bean
    public Counter errorLogsCounter(MeterRegistry registry) {
        return Counter.builder("logpulse.logs.error")
                .description("The number of error logs")
                .register(registry);
    }

    @Bean
    public Counter warnLogsCounter(MeterRegistry registry) {
        return Counter.builder("logpulse.logs.warn")
                .description("The number of warning logs")
                .register(registry);
    }

    @Bean
    public Counter infoLogsCounter(MeterRegistry registry) {
        return Counter.builder("logpulse.logs.info")
                .description("The number of info logs")
                .register(registry);
    }

    @Bean
    public Counter debugLogsCounter(MeterRegistry registry) {
        return Counter.builder("logpulse.logs.debug")
                .description("The number of debug logs")
                .register(registry);
    }
}