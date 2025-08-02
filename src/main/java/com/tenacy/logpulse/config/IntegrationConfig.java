package com.tenacy.logpulse.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.messaging.MessageChannel;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
@Configuration
@EnableIntegration
@IntegrationComponentScan(basePackages = "com.tenacy.logpulse.integration")
public class IntegrationConfig {

    @Bean
    public Executor integrationExecutor() {
        return Executors.newFixedThreadPool(10);
    }

    @Bean
    public MessageChannel logInputChannel() {
        return new DirectChannel();
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
    public MessageChannel processedLogChannel() {
        return new PublishSubscribeChannel(integrationExecutor());
    }

    @Bean
    public MessageChannel errorChannel() {
        return new PublishSubscribeChannel(integrationExecutor());
    }
}