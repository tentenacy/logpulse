package com.tenacy.logpulse.integration.router;

import com.tenacy.logpulse.api.dto.LogEventDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.Router;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class LogRouter {

    private final MessageChannel infoLogChannel;
    private final Map<String, MessageChannel> channelMap;

    public LogRouter(MessageChannel errorLogChannel,
                     MessageChannel warnLogChannel,
                     MessageChannel infoLogChannel,
                     MessageChannel debugLogChannel) {
        this.infoLogChannel = infoLogChannel;
        channelMap = Map.of(
                "ERROR", errorLogChannel,
                "WARN", warnLogChannel,
                "INFO", infoLogChannel,
                "DEBUG", debugLogChannel
        );
    }

    @Router(inputChannel = "logInputChannel")
    public MessageChannel routeByLogLevel(Message<LogEventDto> message) {
        LogEventDto logEvent = message.getPayload();
        String logLevel = logEvent.getLogLevel();

        log.debug("Routing log event with level {} to appropriate channel", logLevel);

        return channelMap.getOrDefault(logLevel, infoLogChannel);
    }
}