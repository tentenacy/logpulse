package com.tenacy.logpulse.integration;

import com.tenacy.logpulse.api.dto.LogEventDto;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.messaging.handler.annotation.Payload;

@MessagingGateway(name = "logGateway", defaultRequestChannel = "logInputChannel")
public interface LogGateway {

    @Gateway
    void processLog(@Payload LogEventDto logEventDto);
}