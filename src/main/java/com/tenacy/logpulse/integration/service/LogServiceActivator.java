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

    @ServiceActivator(inputChannel = "processedLogChannel")
    public LogEventDto processLog(Message<LogEventDto> message) {
        LogEventDto logEvent = message.getPayload();

        try {
            log.debug("로그 이벤트 처리: {}", logEvent);

            // Kafka로 로그 이벤트 전송
            logProducerService.sendLogEvent(logEvent);
            log.debug("로그 이벤트를 Kafka로 전송: {}", logEvent);

            return logEvent;
        } catch (Exception e) {
            log.error("로그 이벤트를 Kafka로 전송하는 중 오류 발생: {}", e.getMessage(), e);

            // 오류가 발생해도 메시지는 계속 전달
            return logEvent;
        }
    }
}