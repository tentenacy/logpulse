package com.tenacy.logpulse.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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

    @Bean
    public Counter integrationReceivedCounter(MeterRegistry registry) {
        return Counter.builder("logpulse.integration.received")
                .description("Number of logs received by integration service")
                .register(registry);
    }

    @Bean
    public Counter integrationErrorCounter(MeterRegistry registry) {
        return Counter.builder("logpulse.integration.errors")
                .description("Number of errors in integration service")
                .register(registry);
    }

    @Bean
    public Counter integrationRetryCounter(MeterRegistry registry) {
        return Counter.builder("logpulse.integration.retries")
                .description("Number of retries in integration service")
                .register(registry);
    }

    @Bean
    public Counter serviceActivatorProcessedCounter(MeterRegistry registry) {
        return Counter.builder("logpulse.activator.processed")
                .description("Number of logs processed by service activator")
                .register(registry);
    }

    @Bean
    public Counter serviceActivatorErrorCounter(MeterRegistry registry) {
        return Counter.builder("logpulse.activator.errors")
                .description("Number of errors in service activator")
                .register(registry);
    }

    @Bean
    public Counter filterReceivedCounter(MeterRegistry registry) {
        return Counter.builder("logpulse.filter.received")
                .description("Number of logs received by filter")
                .register(registry);
    }

    @Bean
    public Counter filterPassedCounter(MeterRegistry registry) {
        return Counter.builder("logpulse.filter.passed")
                .description("Number of logs passed by filter")
                .register(registry);
    }

    @Bean
    public Counter filterRejectedCounter(MeterRegistry registry) {
        return Counter.builder("logpulse.filter.rejected")
                .description("Number of logs rejected by filter")
                .register(registry);
    }

    @Bean
    public Counter inputChannelCounter(MeterRegistry registry) {
        return Counter.builder("logpulse.channel.input")
                .description("Number of messages received on input channel")
                .register(registry);
    }

    @Bean
    public Counter channelErrorCounter(MeterRegistry registry) {
        return Counter.builder("logpulse.channel.errors")
                .description("Number of errors in channel processing")
                .register(registry);
    }

    @Bean
    public Counter performanceTestLogsCounter(MeterRegistry registry) {
        return Counter.builder("logpulse.performance.logs")
                .description("Number of logs generated in performance tests")
                .register(registry);
    }

    @Bean
    public Timer performanceTestTimer(MeterRegistry registry) {
        return Timer.builder("logpulse.performance.duration")
                .description("Duration of performance tests")
                .register(registry);
    }

    @Bean
    public Counter compressionEnabledCounter(MeterRegistry registry) {
        return Counter.builder("logpulse.compression.enabled")
                .description("Number of logs with compression enabled")
                .register(registry);
    }

    @Bean
    public Counter compressionDisabledCounter(MeterRegistry registry) {
        return Counter.builder("logpulse.compression.disabled")
                .description("Number of logs with compression disabled")
                .register(registry);
    }
}