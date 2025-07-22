package com.tenacy.logpulse.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.elasticsearch.service.ElasticsearchService;
import com.tenacy.logpulse.pattern.LogPatternDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
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
    private final RealTimeErrorMonitorService errorMonitorService;
    private final LogPatternDetector patternDetector;
    private final LogCompressionService compressionService;
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

                // 실시간 오류 모니터링
                errorMonitorService.monitorLog(logEventDto);

                // 원본 내용
                String content = logEventDto.getContent();
                int originalSize = content != null ? content.getBytes(StandardCharsets.UTF_8).length : 0;

                // 압축 여부 결정 및 적용
                boolean shouldCompress = compressionService.shouldCompress(content);
                String finalContent = content;
                int compressedSize = originalSize;

                if (shouldCompress) {
                    finalContent = compressionService.compressContent(content);
                    compressedSize = finalContent != null ? finalContent.getBytes(StandardCharsets.UTF_8).length : 0;
                    log.debug("Compressed log content: {}% reduction",
                            Math.round((1 - (double)compressedSize/originalSize) * 100));
                }

                LogEntry logEntry = LogEntry.builder()
                        .source(logEventDto.getSource())
                        .content(finalContent)
                        .logLevel(logEventDto.getLogLevel())
                        .createdAt(logEventDto.getTimestamp() != null ?
                                logEventDto.getTimestamp() : LocalDateTime.now())
                        .compressed(shouldCompress)
                        .originalSize(originalSize)
                        .compressedSize(compressedSize)
                        .build();

                logEntries.add(logEntry);

                try {
                    // 패턴 감지는 압축되지 않은 원본 내용으로 처리
                    LogEntry uncompressedEntry = cloneWithUncompressedContent(logEntry, content);
                    patternDetector.processLog(uncompressedEntry);
                } catch (Exception e) {
                    log.error("Error processing log with pattern detector: {}", e.getMessage(), e);
                }

            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize log event: {}", message, e);
            }
        }

        if (!logEntries.isEmpty()) {
            // JDBC 배치 인서트 사용
            jdbcBatchInsertService.batchInsert(logEntries);
            log.debug("Saved {} log entries to database using JDBC batch update", logEntries.size());

            // Elasticsearch에 한번에 저장 (배치 처리)
            // Elasticsearch에는 압축 해제된 원본 내용을 저장
            elasticsearchService.saveAll(logEntries);
            log.debug("Saved {} log entries to Elasticsearch", logEntries.size());
        }
    }

    /**
     * 압축 해제된 내용으로 로그 엔트리 복제
     */
    private LogEntry cloneWithUncompressedContent(LogEntry entry, String uncompressedContent) {
        return LogEntry.builder()
                .id(entry.getId())
                .source(entry.getSource())
                .content(uncompressedContent)
                .logLevel(entry.getLogLevel())
                .createdAt(entry.getCreatedAt())
                .compressed(false)
                .originalSize(entry.getOriginalSize())
                .compressedSize(entry.getCompressedSize())
                .build();
    }
}