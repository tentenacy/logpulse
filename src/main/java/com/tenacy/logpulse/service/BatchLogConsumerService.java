package com.tenacy.logpulse.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.elasticsearch.service.ElasticsearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchLogConsumerService {

    private final JdbcBatchInsertService jdbcBatchInsertService;
    private final ElasticsearchService elasticsearchService;
    private final LogMetricsService logMetricsService;
    private final LogAlertService logAlertService;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(
            topics = "${logpulse.kafka.topics.raw-logs}",
            groupId = "${spring.kafka.consumer.group-id}",
            concurrency = "${spring.kafka.listener.concurrency:3}",
            batch = "true"
    )
    public void consumeBatchLogEvents(List<String> messages) {
        log.debug("Received batch of {} log events", messages.size());

        List<LogEntry> logEntries = new ArrayList<>(messages.size());

        for (String message : messages) {
            try {
                LogEventDto logEventDto = objectMapper.readValue(message, LogEventDto.class);

                // 메트릭 기록
                logMetricsService.recordLog(logEventDto);

                // 알림 체크
                logAlertService.checkLogForAlert(logEventDto);

                LogEntry logEntry = LogEntry.builder()
                        .source(logEventDto.getSource())
                        .content(logEventDto.getContent())
                        .logLevel(logEventDto.getLogLevel())
                        .createdAt(logEventDto.getTimestamp() != null ?
                                logEventDto.getTimestamp() : LocalDateTime.now())
                        .build();

                logEntries.add(logEntry);

            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize log event: {}", message, e);
            }
        }

        if (!logEntries.isEmpty()) {
            // JDBC 배치 인서트 사용
            jdbcBatchInsertService.batchInsert(logEntries);
            log.debug("Saved {} log entries to database using JDBC batch update", logEntries.size());

            // Elasticsearch에 한번에 저장 (배치 처리)
            elasticsearchService.saveAll(logEntries);
            log.debug("Saved {} log entries to Elasticsearch", logEntries.size());
        }
    }
}