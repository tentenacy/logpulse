package com.tenacy.logpulse.integration.filter;

import com.tenacy.logpulse.api.dto.LogEventDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.Filter;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class LogFilter {

    @Value("${logpulse.filter.excluded-sources:}")
    private String excludedSourcesString;

    @Value("${logpulse.filter.min-level:INFO}")
    private String minLogLevel;

    private List<String> excludedSources;

    @Value("${logpulse.filter.excluded-sources:}")
    public void setExcludedSources(String sources) {
        if (sources != null && !sources.isEmpty()) {
            excludedSources = Arrays.asList(sources.split(","));
            log.info("제외된 소스 설정: {}", excludedSources);
        } else {
            excludedSources = List.of();
            log.info("제외된 소스 없음");
        }
    }

    @Filter(inputChannel = "processedLogChannel", outputChannel = "processedLogChannel")
    public boolean filterLog(Message<LogEventDto> message) {
        LogEventDto logEvent = message.getPayload();

        // 성능 테스트 소스는 항상 통과
        if ("performance-test".equals(logEvent.getSource())) {
            return true;
        }

        // 제외된 소스 필터링
        if (excludedSources != null && excludedSources.contains(logEvent.getSource())) {
            log.debug("제외된 소스의 로그 필터링: {}", logEvent.getSource());
            return false;
        }

        // 로그 레벨 필터링
        int logLevelValue = getLogLevelValue(logEvent.getLogLevel());
        int minLevelValue = getLogLevelValue(minLogLevel);

        boolean passes = logLevelValue >= minLevelValue;

        if (!passes) {
            log.debug("최소 레벨 {} 미만의 로그 레벨 {} 필터링",
                    minLogLevel, logEvent.getLogLevel());
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