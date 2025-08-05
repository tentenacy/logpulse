package com.tenacy.logpulse.integration.transformer;

import com.tenacy.logpulse.api.dto.LogEventDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.Transformer;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Slf4j
public class LogEnricher {

    @Transformer(inputChannel = "errorLogChannel", outputChannel = "enrichedLogChannel")
    public Message<LogEventDto> enrichErrorLog(Message<LogEventDto> message) {
        return enrichLog(message, "ERROR");
    }

    @Transformer(inputChannel = "warnLogChannel", outputChannel = "enrichedLogChannel")
    public Message<LogEventDto> enrichWarnLog(Message<LogEventDto> message) {
        return enrichLog(message, "WARN");
    }

    @Transformer(inputChannel = "infoLogChannel", outputChannel = "enrichedLogChannel")
    public Message<LogEventDto> enrichInfoLog(Message<LogEventDto> message) {
        return enrichLog(message, "INFO");
    }

    @Transformer(inputChannel = "debugLogChannel", outputChannel = "enrichedLogChannel")
    public Message<LogEventDto> enrichDebugLog(Message<LogEventDto> message) {
        return enrichLog(message, "DEBUG");
    }

    private Message<LogEventDto> enrichLog(Message<LogEventDto> message, String level) {
        LogEventDto logEvent = message.getPayload();

        if (logEvent.getTimestamp() == null) {
            logEvent.setTimestamp(LocalDateTime.now());
        }

        return MessageBuilder.withPayload(logEvent)
                .copyHeaders(message.getHeaders())
                .setHeader("processedTime", LocalDateTime.now())
                .setHeader("correlationId", UUID.randomUUID().toString())
                .setHeader("logLevel", level)
                .build();
    }
}