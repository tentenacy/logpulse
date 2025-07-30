package com.tenacy.logpulse.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.elasticsearch.service.ElasticsearchService;
import com.tenacy.logpulse.pattern.LogPatternDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class BatchLogConsumerService {

    private final JdbcBatchInsertService jdbcBatchInsertService;
    private final ElasticsearchService elasticsearchService;
    private final LogMetricsService logMetricsService;
    private final RealTimeErrorMonitorService errorMonitorService;
    private final LogPatternDetector patternDetector;
    private final LogCompressionService compressionService;
    private final ObjectMapper objectMapper;

    @Value("${logpulse.consumer.max-batch-size:1000}")
    private int maxBatchSize;

    public BatchLogConsumerService(JdbcBatchInsertService jdbcBatchInsertService,
                                   ElasticsearchService elasticsearchService,
                                   LogMetricsService logMetricsService,
                                   RealTimeErrorMonitorService errorMonitorService,
                                   LogPatternDetector patternDetector,
                                   LogCompressionService compressionService,
                                   ObjectMapper objectMapper) {
        this.jdbcBatchInsertService = jdbcBatchInsertService;
        this.elasticsearchService = elasticsearchService;
        this.logMetricsService = logMetricsService;
        this.errorMonitorService = errorMonitorService;
        this.patternDetector = patternDetector;
        this.compressionService = compressionService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    @KafkaListener(
            topics = "${logpulse.kafka.topics.raw-logs}",
            groupId = "${spring.kafka.consumer.group-id}",
            concurrency = "${spring.kafka.listener.concurrency:3}",
            batch = "true"
    )
    public void consumeBatchLogEvents(List<String> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        int batchSize = messages.size();
        log.debug("{}개의 로그 이벤트 배치 수신", batchSize);

        // 너무 큰 배치는 나누어 처리
        if (batchSize > maxBatchSize) {
            processSplitBatches(messages);
            return;
        }

        List<LogEntry> logEntries = new ArrayList<>(batchSize);
        List<LogEntry> patternDetectionEntries = new ArrayList<>(batchSize);

        for (String message : messages) {
            try {
                LogEventDto logEventDto = objectMapper.readValue(message, LogEventDto.class);

                // 메트릭 기록
                logMetricsService.recordLog(logEventDto);

                // 실시간 오류 모니터링
                if ("ERROR".equalsIgnoreCase(logEventDto.getLogLevel())) {
                    errorMonitorService.monitorLog(logEventDto);
                }

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

                // 패턴 감지를 위한 원본 내용 보존
                LogEntry uncompressedEntry = LogEntry.builder()
                        .source(logEntry.getSource())
                        .content(content)
                        .logLevel(logEntry.getLogLevel())
                        .createdAt(logEntry.getCreatedAt())
                        .compressed(false)
                        .originalSize(originalSize)
                        .compressedSize(compressedSize)
                        .build();
                patternDetectionEntries.add(uncompressedEntry);

            } catch (JsonProcessingException e) {
                log.error("로그 이벤트 역직렬화 실패: {}", message, e);
            }
        }

        // 배치 삽입
        if (!logEntries.isEmpty()) {
            try {
                jdbcBatchInsertService.batchInsert(logEntries);
                log.debug("JDBC 배치 업데이트를 사용하여 {}개 로그 항목 저장 완료", logEntries.size());
            } catch (Exception e) {
                log.error("로그를 데이터베이스에 저장하는 중 오류 발생: {}", e.getMessage(), e);
            }

            try {
                elasticsearchService.saveAll(logEntries);
                log.debug("Elasticsearch에 {}개 로그 항목 인덱싱 완료", logEntries.size());
            } catch (Exception e) {
                log.error("로그를 Elasticsearch에 인덱싱하는 중 오류 발생: {}", e.getMessage(), e);
            }
        }

        // 패턴 감지 처리
        if (!patternDetectionEntries.isEmpty()) {
            processPatternDetection(patternDetectionEntries);
        }
    }

    private void processSplitBatches(List<String> messages) {
        int batchSize = messages.size();
        int batches = (batchSize + maxBatchSize - 1) / maxBatchSize;

        log.info("{}개 메시지의 대규모 배치를 {}개 작은 배치로 분할", batchSize, batches);

        for (int i = 0; i < batches; i++) {
            int fromIndex = i * maxBatchSize;
            int toIndex = Math.min(fromIndex + maxBatchSize, batchSize);

            List<String> subBatch = messages.subList(fromIndex, toIndex);
            log.debug("서브배치 {}/{} 처리: {}개 메시지", i+1, batches, subBatch.size());

            // 재귀적으로 처리 (이제 분할된 배치는 항상 maxBatchSize 이하)
            consumeBatchLogEvents(subBatch);
        }
    }

    private void processPatternDetection(List<LogEntry> entries) {
        try {
            for (LogEntry entry : entries) {
                try {
                    patternDetector.processLog(entry);
                } catch (Exception e) {
                    log.error("패턴 감지기로 로그 처리 중 오류 발생: {}", e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("배치 패턴 감지 중 오류 발생: {}", e.getMessage(), e);
        }
    }
}