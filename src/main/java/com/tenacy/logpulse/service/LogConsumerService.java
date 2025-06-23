package com.tenacy.logpulse.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.domain.LogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogConsumerService {

    private final LogRepository logRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${logpulse.kafka.topics.raw-logs}", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeLogEvent(String message) {
        try {
            LogEventDto logEventDto = objectMapper.readValue(message, LogEventDto.class);
            log.debug("Received log event from Kafka: {}", logEventDto);

            LogEntry logEntry = LogEntry.builder()
                    .source(logEventDto.getSource())
                    .content(logEventDto.getContent())
                    .logLevel(logEventDto.getLogLevel())
                    .build();

            logRepository.save(logEntry);
            log.debug("Saved log entry to database: {}", logEntry);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize log event: {}", message, e);
        }
    }
}