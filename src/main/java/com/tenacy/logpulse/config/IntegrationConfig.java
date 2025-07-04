package com.tenacy.logpulse.config;

import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.service.LogProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.MessageChannel;

@Slf4j
@Configuration
@EnableIntegration
@IntegrationComponentScan(basePackages = "com.tenacy.logpulse.integration")
@RequiredArgsConstructor
public class IntegrationConfig {

    private final LogProducerService logProducerService;

    @Bean
    public MessageChannel logInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel processedLogChannel() {
        return new DirectChannel();
    }

    @Bean
    public IntegrationFlow logProcessingFlow() {
        return IntegrationFlow.from("logInputChannel")
                // 페이로드 타입 체크
                .<Object, Object>transform(payload -> {
                    if (payload instanceof LogEventDto) {
                        return payload;
                    }
                    log.warn("Unexpected payload type: {}",
                            payload != null ? payload.getClass().getName() : "null");
                    return payload;
                })
                // 메시지 처리
                .<LogEventDto>handle((logEvent, headers) -> {
                    try {
                        // test-source 필터링
                        if ("test-source".equals(logEvent.getSource())) {
                            return null;
                        }

                        // 타임스탬프 및 로그 레벨 처리
                        if (logEvent.getTimestamp() == null) {
                            logEvent.setTimestamp(java.time.LocalDateTime.now());
                        }

                        // Kafka로 로그 이벤트 전송
                        logProducerService.sendLogEvent(logEvent);
                        log.debug("Log processing completed: {}", logEvent);
                    } catch (Exception e) {
                        log.error("Error processing log event: {}", e.getMessage(), e);
                    }
                    return null;
                })
                .channel("processedLogChannel")
                .get();
    }
}