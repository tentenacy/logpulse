package com.tenacy.logpulse.integration.service;

import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.service.LogProducerService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LogServiceActivator {

    private final LogProducerService logProducerService;
    private final Counter serviceActivatorProcessedCounter;
    private final Counter serviceActivatorErrorCounter;
    private final MeterRegistry meterRegistry;

    @ServiceActivator(inputChannel = "filteredLogChannel", outputChannel = "processedLogChannel")
    public LogEventDto processLog(Message<LogEventDto> message) {
        LogEventDto logEvent = message.getPayload();

        // 타이머로 처리 시간 측정
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            // 처리 카운터 증가
            serviceActivatorProcessedCounter.increment();

            // 로그 레벨별 카운터 추가
            meterRegistry.counter("logpulse.activator.level", "level", logEvent.getLogLevel().toUpperCase()).increment();

            log.debug("Processing log event: {}", logEvent);

            // Kafka로 로그 이벤트 전송
            logProducerService.sendLogEvent(logEvent);
            log.debug("Log event sent to Kafka: {}", logEvent);

            // 타이머 종료 및 기록
            sample.stop(meterRegistry.timer("logpulse.activator.processing.time"));

            return logEvent;
        } catch (Exception e) {
            // 오류 카운터 증가
            serviceActivatorErrorCounter.increment();

            log.error("Failed to send log event to Kafka: {}", e.getMessage(), e);

            // 타이머 종료 및 기록 (오류 상태 태그 추가)
            sample.stop(meterRegistry.timer("logpulse.activator.processing.time", "status", "error"));

            // 오류가 발생해도 메시지는 계속 전달
            // 오류를 던지지 않고 로그 이벤트를 반환하여 처리 흐름 계속 유지
            return logEvent;
        }
    }

    @Scheduled(fixedRate = 60000)
    public void logStats() {
        double processed = serviceActivatorProcessedCounter.count();
        double errors = serviceActivatorErrorCounter.count();

        if (processed > 0) {
            double errorRate = (errors / processed) * 100.0;
            log.info("Service Activator stats - Processed: {}, Errors: {} ({:.2f}%)",
                    (long)processed, (long)errors, errorRate);
        }
    }
}