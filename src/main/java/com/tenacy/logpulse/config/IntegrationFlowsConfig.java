package com.tenacy.logpulse.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.handler.LoggingHandler;

@Configuration
@Slf4j
public class IntegrationFlowsConfig {

    @Bean
    public IntegrationFlow errorHandlerFlow() {
        return IntegrationFlow.from("errorChannel")
                .log(LoggingHandler.Level.ERROR,
                        message -> {
                            if (message.getPayload() instanceof Exception e) {
                                return String.format("통합 흐름 오류: %s", e.getMessage());
                            }
                            return "통합 흐름 내 알 수 없는 오류";
                        })
                .handle(message -> {
                    log.error("통합 흐름 오류: {}", message);
                })
                .get();
    }
}