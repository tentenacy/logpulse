package com.tenacy.logpulse.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.domain.LogRepository;
import com.tenacy.logpulse.elasticsearch.service.ElasticsearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogConsumerService {

    private final LogRepository logRepository;
    private final ElasticsearchService elasticsearchService;
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

            // MySQL에 저장
            LogEntry savedEntry = logRepository.save(logEntry);
            log.debug("Saved log entry to database: {}", savedEntry);

            // Elasticsearch에 저장
            elasticsearchService.saveLog(savedEntry);
            log.debug("Saved log entry to Elasticsearch: {}", savedEntry);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize log event: {}", message, e);
        }
    }
}