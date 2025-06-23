package com.tenacy.logpulse.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenacy.logpulse.api.dto.LogEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${logpulse.kafka.topics.raw-logs}")
    private String rawLogsTopic;

    public void sendLogEvent(LogEventDto logEventDto) {
        try {
            String logEventJson = objectMapper.writeValueAsString(logEventDto);
            kafkaTemplate.send(rawLogsTopic, logEventDto.getSource(), logEventJson);
            log.debug("Sent log event to Kafka: {}", logEventJson);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize log event: {}", logEventDto, e);
        }
    }
}