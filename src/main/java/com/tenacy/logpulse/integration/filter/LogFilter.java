package com.tenacy.logpulse.integration.filter;

import com.tenacy.logpulse.api.dto.LogEventDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.Filter;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.List;

//@Component
@Slf4j
public class LogFilter {

    @Value("${logpulse.filter.excluded-sources:}")
    private List<String> excludedSources;

    @Value("${logpulse.filter.min-level:INFO}")
    private String minLogLevel;

    @Filter(inputChannel = "enrichedLogChannel", outputChannel = "filteredLogChannel")
    public boolean filterLog(Message<LogEventDto> message) {
        LogEventDto logEvent = message.getPayload();

        // 제외된 소스에서 온 로그는 필터링
        if (excludedSources.contains(logEvent.getSource())) {
            log.debug("Filtering out log from excluded source: {}", logEvent.getSource());
            return false;
        }

        // 최소 로그 레벨 기준으로 필터링
        int logLevelValue = getLogLevelValue(logEvent.getLogLevel());
        int minLevelValue = getLogLevelValue(minLogLevel);

        boolean passes = logLevelValue >= minLevelValue;

        if (!passes) {
            log.debug("Filtering out log with level {} below minimum level {}",
                    logEvent.getLogLevel(), minLogLevel);
        }

        return passes;
    }

    private int getLogLevelValue(String level) {
        return switch (level.toUpperCase()) {
            case "ERROR" -> 4;
            case "WARN" -> 3;
            case "INFO" -> 2;
            case "DEBUG" -> 1;
            default -> 0;
        };
    }
}