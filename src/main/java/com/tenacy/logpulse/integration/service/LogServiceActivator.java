package com.tenacy.logpulse.integration.service;

import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.service.LogProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LogServiceActivator {

    private final LogProducerService logProducerService;

    @ServiceActivator(inputChannel = "filteredLogChannel")
    public LogEventDto processLog(Message<LogEventDto> message) {
        LogEventDto logEvent = message.getPayload();

        log.debug("Processing log event: {}", logEvent);

        // Kafka로 로그 이벤트 전송
        logProducerService.sendLogEvent(logEvent);

        return logEvent;
    }
}