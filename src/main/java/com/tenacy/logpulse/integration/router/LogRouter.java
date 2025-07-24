package com.tenacy.logpulse.integration.router;

import com.tenacy.logpulse.api.dto.LogEventDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.Router;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
public class LogRouter {

    private final MessageChannel errorLogChannel;
    private final MessageChannel warnLogChannel;
    private final MessageChannel infoLogChannel;
    private final MessageChannel debugLogChannel;
    private final Map<String, MessageChannel> channelMap;

    public LogRouter(MessageChannel errorLogChannel,
                     MessageChannel warnLogChannel,
                     MessageChannel infoLogChannel,
                     MessageChannel debugLogChannel) {
        this.errorLogChannel = errorLogChannel;
        this.warnLogChannel = warnLogChannel;
        this.infoLogChannel = infoLogChannel;
        this.debugLogChannel = debugLogChannel;

        this.channelMap = Map.of(
                "ERROR", errorLogChannel,
                "WARN", warnLogChannel,
                "INFO", infoLogChannel,
                "DEBUG", debugLogChannel
        );
    }

    @Router
    public MessageChannel routeByLogLevel(Message<LogEventDto> message) {
        LogEventDto logEvent = message.getPayload();
        String logLevel = logEvent.getLogLevel().toUpperCase();

        log.debug("Routing log event with level {} to appropriate channel", logLevel);

        return channelMap.getOrDefault(logLevel, infoLogChannel);
    }
}