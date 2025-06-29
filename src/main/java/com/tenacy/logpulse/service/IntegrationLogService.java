package com.tenacy.logpulse.service;

import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.integration.LogGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationLogService {

    private final LogGateway logGateway;

    public void processLog(LogEventDto logEventDto) {
        log.debug("Submitting log event to integration pipeline: {}", logEventDto);
        logGateway.processLog(logEventDto);
    }
}