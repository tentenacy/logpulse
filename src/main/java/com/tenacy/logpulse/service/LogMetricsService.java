package com.tenacy.logpulse.service;

import com.tenacy.logpulse.api.dto.LogEventDto;
import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogMetricsService {

    private final Counter processedLogsCounter;
    private final Counter errorLogsCounter;
    private final Counter warnLogsCounter;
    private final Counter infoLogsCounter;
    private final Counter debugLogsCounter;

    public void recordLog(LogEventDto logEventDto) {
        processedLogsCounter.increment();

        switch (logEventDto.getLogLevel().toUpperCase()) {
            case "ERROR":
                errorLogsCounter.increment();
                break;
            case "WARN":
                warnLogsCounter.increment();
                break;
            case "INFO":
                infoLogsCounter.increment();
                break;
            case "DEBUG":
                debugLogsCounter.increment();
                break;
            default:
                // 기본적으로 INFO로 간주
                infoLogsCounter.increment();
        }
    }
}