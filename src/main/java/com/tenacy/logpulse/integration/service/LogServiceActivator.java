package com.tenacy.logpulse.integration.service;

import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.service.LogProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
@Slf4j
public class LogServiceActivator {

    private final LogProducerService logProducerService;
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    @ServiceActivator(inputChannel = "filteredLogChannel", outputChannel = "processedLogChannel")
    public LogEventDto processLog(Message<LogEventDto> message) {
        LogEventDto logEvent = message.getPayload();
        long currentCount = processedCount.incrementAndGet();

        if (currentCount % 10000 == 0) {
            log.info("LogServiceActivator stats - Processed: {}, Errors: {}",
                    processedCount.get(), errorCount.get());
        }

        log.debug("Processing log event: {}", logEvent);

        try {
            // Kafka로 로그 이벤트 전송
            logProducerService.sendLogEvent(logEvent);
            log.debug("Log event sent to Kafka: {}", logEvent);

            return logEvent;
        } catch (Exception e) {
            errorCount.incrementAndGet();
            log.error("Failed to send log event to Kafka: {}", e.getMessage(), e);

            // 오류가 발생해도 메시지는 계속 전달
            // 오류를 던지지 않고 로그 이벤트를 반환하여 처리 흐름 계속 유지
            return logEvent;
        }
    }
}