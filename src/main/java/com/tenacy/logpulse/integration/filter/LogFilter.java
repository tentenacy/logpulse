package com.tenacy.logpulse.integration.filter;

import com.tenacy.logpulse.api.dto.LogEventDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.Filter;
import org.springframework.messaging.Message;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class LogFilter {

    private final Counter filterReceivedCounter;
    private final Counter filterPassedCounter;
    private final Counter filterRejectedCounter;
    private final MeterRegistry meterRegistry;

    @Value("${logpulse.filter.excluded-sources:}")
    private String excludedSourcesString;

    @Value("${logpulse.filter.min-level:INFO}")
    private String minLogLevel;

    private List<String> excludedSources;

    @Value("${logpulse.filter.excluded-sources:}")
    public void setExcludedSources(String sources) {
        if (sources != null && !sources.isEmpty()) {
            excludedSources = Arrays.asList(sources.split(","));
            log.info("Configured excluded sources: {}", excludedSources);
        } else {
            excludedSources = List.of();
            log.info("No excluded sources configured");
        }
    }

    @Filter(inputChannel = "enrichedLogChannel", outputChannel = "filteredLogChannel")
    public boolean filterLog(Message<LogEventDto> message) {
        // 수신 카운터 증가
        filterReceivedCounter.increment();

        LogEventDto logEvent = message.getPayload();

        // performance-test 소스는 항상 통과시킴 (성능 테스트용)
        if ("performance-test".equals(logEvent.getSource())) {
            // 통과 카운터 증가
            filterPassedCounter.increment();

            // 소스별 통과 카운터
            meterRegistry.counter("logpulse.filter.source.passed", "source", "performance-test").increment();

            return true;
        }

        if (excludedSources != null && excludedSources.contains(logEvent.getSource())) {
            log.debug("Filtering out log from excluded source: {}", logEvent.getSource());

            // 거부 카운터 증가
            filterRejectedCounter.increment();

            // 소스별 거부 카운터
            meterRegistry.counter("logpulse.filter.source.rejected", "source", logEvent.getSource()).increment();

            return false;
        }

        int logLevelValue = getLogLevelValue(logEvent.getLogLevel());
        int minLevelValue = getLogLevelValue(minLogLevel);

        boolean passes = logLevelValue >= minLevelValue;

        if (!passes) {
            log.debug("Filtering out log with level {} below minimum level {}",
                    logEvent.getLogLevel(), minLogLevel);

            // 거부 카운터 증가
            filterRejectedCounter.increment();

            // 레벨별 거부 카운터
            meterRegistry.counter("logpulse.filter.level.rejected", "level", logEvent.getLogLevel()).increment();

        } else {
            // 통과 카운터 증가
            filterPassedCounter.increment();

            // 레벨별 통과 카운터
            meterRegistry.counter("logpulse.filter.level.passed", "level", logEvent.getLogLevel()).increment();
        }

        return passes;
    }

    private int getLogLevelValue(String level) {
        if (level == null) {
            return 0;
        }

        return switch (level.toUpperCase()) {
            case "ERROR" -> 4;
            case "WARN" -> 3;
            case "INFO" -> 2;
            case "DEBUG" -> 1;
            default -> 0;
        };
    }

    @Scheduled(fixedRate = 60000)
    public void logStats() {
        double received = filterReceivedCounter.count();
        double passed = filterPassedCounter.count();
        double rejected = filterRejectedCounter.count();

        if (received > 0) {
            double passRate = (passed / received) * 100.0;
            double rejectRate = (rejected / received) * 100.0;

            log.info("Filter stats - Received: {}, Passed: {} ({:.2f}%), Rejected: {} ({:.2f}%)",
                    (long)received, (long)passed, passRate, (long)rejected, rejectRate);
        }
    }
}