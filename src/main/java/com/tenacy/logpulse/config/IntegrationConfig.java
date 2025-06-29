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
import org.springframework.integration.dsl.MessageChannels;
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
        return MessageChannels.direct().getObject();
    }

    @Bean
    public MessageChannel errorLogChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel warnLogChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel infoLogChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel debugLogChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel enrichedLogChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel filteredLogChannel() {
        return new DirectChannel();
    }

    @Bean
    public IntegrationFlow logProcessingFlow() {
        return IntegrationFlow.from("logInputChannel")
                .<LogEventDto, String>route(LogEventDto::getLogLevel, mapping -> mapping
                        .subFlowMapping("ERROR", sf -> sf.channel("errorLogChannel"))
                        .subFlowMapping("WARN", sf -> sf.channel("warnLogChannel"))
                        .subFlowMapping("INFO", sf -> sf.channel("infoLogChannel"))
                        .subFlowMapping("DEBUG", sf -> sf.channel("debugLogChannel"))
                        .defaultSubFlowMapping(sf -> sf.channel("infoLogChannel")))
                .get();
    }

    @Bean
    public IntegrationFlow errorLogFlow() {
        return IntegrationFlow.from("errorLogChannel")
                .enrichHeaders(h -> h
                        .headerExpression("processedTime", "T(java.time.LocalDateTime).now()")
                        .headerExpression("correlationId", "T(java.util.UUID).randomUUID().toString()"))
                .channel("enrichedLogChannel")
                .get();
    }

    @Bean
    public IntegrationFlow warnLogFlow() {
        return IntegrationFlow.from("warnLogChannel")
                .enrichHeaders(h -> h
                        .headerExpression("processedTime", "T(java.time.LocalDateTime).now()")
                        .headerExpression("correlationId", "T(java.util.UUID).randomUUID().toString()"))
                .channel("enrichedLogChannel")
                .get();
    }

    @Bean
    public IntegrationFlow infoLogFlow() {
        return IntegrationFlow.from("infoLogChannel")
                .enrichHeaders(h -> h
                        .headerExpression("processedTime", "T(java.time.LocalDateTime).now()")
                        .headerExpression("correlationId", "T(java.util.UUID).randomUUID().toString()"))
                .channel("enrichedLogChannel")
                .get();
    }

    @Bean
    public IntegrationFlow debugLogFlow() {
        return IntegrationFlow.from("debugLogChannel")
                .enrichHeaders(h -> h
                        .headerExpression("processedTime", "T(java.time.LocalDateTime).now()")
                        .headerExpression("correlationId", "T(java.util.UUID).randomUUID().toString()"))
                .channel("enrichedLogChannel")
                .get();
    }

    @Bean
    public IntegrationFlow enrichedLogFlow() {
        return IntegrationFlow.from("enrichedLogChannel")
                .filter(message -> {
                    LogEventDto logEvent = (LogEventDto) message;
                    return !logEvent.getSource().equals("test-source");
                })
                .channel("filteredLogChannel")
                .get();
    }

    @Bean
    public IntegrationFlow filteredLogFlow() {
        return IntegrationFlow.from("filteredLogChannel")
                .handle(message -> {
                    LogEventDto logEvent = (LogEventDto) message;
                    logProducerService.sendLogEvent(logEvent);
                    log.debug("Log processing completed: {}", logEvent);
                })
                .get();
    }
}