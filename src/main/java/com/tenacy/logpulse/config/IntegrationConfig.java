package com.tenacy.logpulse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.messaging.MessageChannel;

@Configuration
@EnableIntegration
@IntegrationComponentScan(basePackages = "com.tenacy.logpulse.integration")
public class IntegrationConfig {

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
    public MessageChannel outputLogChannel() {
        return new DirectChannel();
    }
}