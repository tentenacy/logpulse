package com.tenacy.logpulse.integration.filter;

import com.tenacy.logpulse.api.dto.LogEventDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.Filter;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class LogFilter {

    private final AtomicLong totalReceived = new AtomicLong(0);
    private final AtomicLong totalPassed = new AtomicLong(0);
    private final AtomicLong totalFiltered = new AtomicLong(0);

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
        long received = totalReceived.incrementAndGet();
        LogEventDto logEvent = message.getPayload();

        if (received % 10000 == 0) {
            log.info("LogFilter stats - Received: {}, Passed: {}, Filtered: {}",
                    totalReceived.get(), totalPassed.get(), totalFiltered.get());
        }

        // performance-test 소스는 항상 통과시킴 (성능 테스트용)
        if ("performance-test".equals(logEvent.getSource())) {
            totalPassed.incrementAndGet();
            return true;
        }

        if (excludedSources != null && excludedSources.contains(logEvent.getSource())) {
            log.debug("Filtering out log from excluded source: {}", logEvent.getSource());
            totalFiltered.incrementAndGet();
            return false;
        }

        int logLevelValue = getLogLevelValue(logEvent.getLogLevel());
        int minLevelValue = getLogLevelValue(minLogLevel);

        boolean passes = logLevelValue >= minLevelValue;

        if (!passes) {
            log.debug("Filtering out log with level {} below minimum level {}",
                    logEvent.getLogLevel(), minLogLevel);
            totalFiltered.incrementAndGet();
        } else {
            totalPassed.incrementAndGet();
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
}