package com.tenacy.logpulse.config;

import com.tenacy.logpulse.api.dto.LogEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

import java.util.function.Function;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class IntegrationFlowsConfig {

    @Bean
    public IntegrationFlow errorHandlerFlow() {
        return IntegrationFlow.from("errorChannel")
                .log(LoggingHandler.Level.ERROR,
                        message -> {
                            if (message.getPayload() instanceof Exception e) {
                                return String.format("Error in integration flow: %s", e.getMessage());
                            }
                            return "Unknown error in integration flow";
                        })
                .handle(message -> {
                    log.error("Integration flow error: {}", message);
                })
                .get();
    }

    @Bean
    public IntegrationFlow debugLoggerFlow() {
        return IntegrationFlow.from("processedLogChannel")
                .wireTap(flow -> flow
                        .handle(message -> {
                            LogEventDto payload = (LogEventDto) message.getPayload();
                            MessageHeaders headers = message.getHeaders();
                            log.debug("Processed log: [{}] {} - {} [headers: {}]",
                                    payload.getLogLevel(),
                                    payload.getSource(),
                                    payload.getContent().length() > 50 ?
                                            payload.getContent().substring(0, 50) + "..." :
                                            payload.getContent(),
                                    headers);
                        }))
                .nullChannel();
    }

    private <T> Function<Message<T>, Message<T>> addTraceHeader() {
        return message -> {
            // Use MessageBuilder to add a tracing header
            return org.springframework.messaging.support.MessageBuilder
                    .fromMessage(message)
                    .setHeader("X-Trace-Id", java.util.UUID.randomUUID().toString())
                    .build();
        };
    }
}