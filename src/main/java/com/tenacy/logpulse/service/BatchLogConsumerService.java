package com.tenacy.logpulse.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.elasticsearch.service.ElasticsearchService;
import com.tenacy.logpulse.pattern.LogPatternDetector;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class BatchLogConsumerService {

    private final JdbcBatchInsertService jdbcBatchInsertService;
    private final ElasticsearchService elasticsearchService;
    private final LogMetricsService logMetricsService;
    private final RealTimeErrorMonitorService errorMonitorService;
    private final LogPatternDetector patternDetector;
    private final LogCompressionService compressionService;
    private final CompressionStatsService compressionStatsService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private final Counter batchesProcessedCounter;
    private final Counter messagesProcessedCounter;
    private final Counter messageDeserializationErrorCounter;
    private final Counter dbInsertErrorCounter;
    private final Counter esIndexingErrorCounter;
    private final Counter patternDetectionErrorCounter;

    private final AtomicInteger batchSizeGauge = new AtomicInteger(0);
    private final AtomicLong lastProcessingTimeGauge = new AtomicLong(0);
    private final AtomicLong totalMessagesGauge = new AtomicLong(0);
    private final AtomicLong processedPerSecondGauge = new AtomicLong(0);

    @Value("${logpulse.consumer.max-batch-size:1000}")
    private int maxBatchSize;

    @Value("${logpulse.consumer.direct-es-threshold:500}")
    private int directEsThreshold;

    public BatchLogConsumerService(JdbcBatchInsertService jdbcBatchInsertService,
                                   ElasticsearchService elasticsearchService,
                                   LogMetricsService logMetricsService,
                                   RealTimeErrorMonitorService errorMonitorService,
                                   LogPatternDetector patternDetector,
                                   LogCompressionService compressionService,
                                   CompressionStatsService compressionStatsService,
                                   ObjectMapper objectMapper,
                                   MeterRegistry meterRegistry) {
        this.jdbcBatchInsertService = jdbcBatchInsertService;
        this.elasticsearchService = elasticsearchService;
        this.logMetricsService = logMetricsService;
        this.errorMonitorService = errorMonitorService;
        this.patternDetector = patternDetector;
        this.compressionService = compressionService;
        this.compressionStatsService = compressionStatsService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;

        // 메트릭 등록
        this.batchesProcessedCounter = Counter.builder("logpulse.kafka.batches.processed")
                .description("Number of Kafka message batches processed")
                .register(meterRegistry);

        this.messagesProcessedCounter = Counter.builder("logpulse.kafka.messages.processed")
                .description("Number of Kafka messages processed")
                .register(meterRegistry);

        this.messageDeserializationErrorCounter = Counter.builder("logpulse.kafka.errors.deserialization")
                .description("Number of Kafka message deserialization errors")
                .register(meterRegistry);

        this.dbInsertErrorCounter = Counter.builder("logpulse.kafka.errors.db")
                .description("Number of database insert errors during Kafka processing")
                .register(meterRegistry);

        this.esIndexingErrorCounter = Counter.builder("logpulse.kafka.errors.es")
                .description("Number of Elasticsearch indexing errors during Kafka processing")
                .register(meterRegistry);

        this.patternDetectionErrorCounter = Counter.builder("logpulse.kafka.errors.pattern")
                .description("Number of pattern detection errors during Kafka processing")
                .register(meterRegistry);

        // 게이지 등록
        meterRegistry.gauge("logpulse.kafka.batch.size", batchSizeGauge);
        meterRegistry.gauge("logpulse.kafka.processing.time", lastProcessingTimeGauge);
        meterRegistry.gauge("logpulse.kafka.messages.total", totalMessagesGauge);
        meterRegistry.gauge("logpulse.kafka.messages.per.second", processedPerSecondGauge);
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

        // 배치 처리 시작 시간
        Timer.Sample batchProcessingTimer = Timer.start(meterRegistry);
        long startTime = System.currentTimeMillis();

        // 배치 크기 업데이트
        int batchSize = messages.size();
        batchSizeGauge.set(batchSize);

        log.debug("Received batch of {} log events", batchSize);
        batchesProcessedCounter.increment();

        // 너무 큰 배치는 나누어 처리
        if (batchSize > maxBatchSize) {
            processSplitBatches(messages);
            return;
        }

        List<LogEntry> logEntries = new ArrayList<>(batchSize);
        List<LogEntry> patternDetectionEntries = new ArrayList<>(batchSize);

        for (String message : messages) {
            try {
                // 메시지 처리 시간 측정
                Timer.Sample messageProcessingTimer = Timer.start(meterRegistry);

                LogEventDto logEventDto = objectMapper.readValue(message, LogEventDto.class);
                messagesProcessedCounter.increment();
                totalMessagesGauge.incrementAndGet();

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

                    // 타이머에 압축 태그 추가
                    messageProcessingTimer.stop(meterRegistry.timer("logpulse.kafka.message.time",
                            "compressed", "true"));
                } else {
                    messageProcessingTimer.stop(meterRegistry.timer("logpulse.kafka.message.time",
                            "compressed", "false"));
                }

                // 압축 통계 기록
                compressionStatsService.recordCompression(shouldCompress, originalSize, compressedSize);

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
                LogEntry uncompressedEntry = cloneWithUncompressedContent(logEntry, content);
                patternDetectionEntries.add(uncompressedEntry);

            } catch (JsonProcessingException e) {
                messageDeserializationErrorCounter.increment();
                log.error("Failed to deserialize log event: {}", message, e);
            }
        }

        // 배치 삽입 시간 측정
        if (!logEntries.isEmpty()) {
            try {
                Timer.Sample dbInsertTimer = Timer.start(meterRegistry);

                // JDBC 배치 인서트 사용
                jdbcBatchInsertService.batchInsert(logEntries);

                dbInsertTimer.stop(meterRegistry.timer("logpulse.kafka.db.insert.time"));
                log.debug("Saved {} log entries to database using JDBC batch update", logEntries.size());
            } catch (Exception e) {
                dbInsertErrorCounter.increment();
                log.error("Failed to save logs to database: {}", e.getMessage(), e);
            }

            try {
                Timer.Sample esIndexingTimer = Timer.start(meterRegistry);

                // Elasticsearch에 한번에 저장 (배치 처리)
                // 작은 배치는 직접 인덱싱, 큰 배치는 비동기 인덱싱
                if (logEntries.size() <= directEsThreshold) {
                    elasticsearchService.saveAll(logEntries);
                    esIndexingTimer.stop(meterRegistry.timer("logpulse.kafka.es.indexing.time",
                            "type", "direct"));
                } else {
                    elasticsearchService.saveAllAsync(logEntries);
                    esIndexingTimer.stop(meterRegistry.timer("logpulse.kafka.es.indexing.time",
                            "type", "async"));
                }

                log.debug("Indexed {} log entries to Elasticsearch", logEntries.size());
            } catch (Exception e) {
                esIndexingErrorCounter.increment();
                log.error("Failed to index logs to Elasticsearch: {}", e.getMessage(), e);
            }
        }

        // 패턴 감지 처리 (별도 스레드에서 비동기적으로 처리)
        if (!patternDetectionEntries.isEmpty()) {
            processPatternDetectionAsync(patternDetectionEntries);
        }

        // 전체 배치 처리 시간 기록
        long processingTime = System.currentTimeMillis() - startTime;
        lastProcessingTimeGauge.set(processingTime);

        batchProcessingTimer.stop(meterRegistry.timer("logpulse.kafka.batch.processing.time"));

        // 처리 성능 계산 및 메트릭 업데이트
        if (processingTime > 0) {
            double messagesPerSecond = batchSize * 1000.0 / processingTime;
            processedPerSecondGauge.set((long)messagesPerSecond);

            if (batchSize > 0) {
                double timePerMessage = (double) processingTime / batchSize;
                meterRegistry.gauge("logpulse.kafka.time.per.message", timePerMessage);
            }
        }
    }

    private void processSplitBatches(List<String> messages) {
        int batchSize = messages.size();
        int batches = (batchSize + maxBatchSize - 1) / maxBatchSize;

        log.info("Splitting large batch of {} messages into {} smaller batches", batchSize, batches);

        for (int i = 0; i < batches; i++) {
            int fromIndex = i * maxBatchSize;
            int toIndex = Math.min(fromIndex + maxBatchSize, batchSize);

            List<String> subBatch = messages.subList(fromIndex, toIndex);
            log.debug("Processing sub-batch {}/{}: {} messages", i+1, batches, subBatch.size());

            // 재귀적으로 처리 (이제 분할된 배치는 항상 maxBatchSize 이하)
            consumeBatchLogEvents(subBatch);
        }
    }

    @Async("patternDetectionExecutor")
    public void processPatternDetectionAsync(List<LogEntry> entries) {
        Timer.Sample patternProcessingTimer = Timer.start(meterRegistry);

        try {
            for (LogEntry entry : entries) {
                try {
                    patternDetector.processLog(entry);
                } catch (Exception e) {
                    patternDetectionErrorCounter.increment();
                    log.error("Error processing log with pattern detector: {}", e.getMessage(), e);
                }
            }

            meterRegistry.counter("logpulse.pattern.detection.processed",
                    "count", String.valueOf(entries.size())).increment();

            patternProcessingTimer.stop(meterRegistry.timer("logpulse.kafka.pattern.detection.time",
                    "result", "success"));

        } catch (Exception e) {
            patternDetectionErrorCounter.increment();
            log.error("Error in batch pattern detection: {}", e.getMessage(), e);

            patternProcessingTimer.stop(meterRegistry.timer("logpulse.kafka.pattern.detection.time",
                    "result", "error"));
        }
    }

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

    @Scheduled(fixedRate = 60000)
    public void logProcessingStats() {
        double batches = batchesProcessedCounter.count();
        double messages = messagesProcessedCounter.count();
        double errors = messageDeserializationErrorCounter.count() +
                dbInsertErrorCounter.count() +
                esIndexingErrorCounter.count() +
                patternDetectionErrorCounter.count();

        if (batches > 0) {
            double avgBatchSize = messages / batches;
            double errorRate = messages > 0 ? (errors / messages) * 100.0 : 0;

            log.info("Kafka processing stats - Batches: {}, Messages: {}, Avg batch size: {:.1f}, " +
                            "Errors: {} ({:.2f}%), Last batch time: {} ms, Rate: {:.1f} msgs/sec",
                    (long)batches,
                    (long)messages,
                    avgBatchSize,
                    (long)errors,
                    errorRate,
                    lastProcessingTimeGauge.get(),
                    processedPerSecondGauge.get());

            // 주요 게이지 값 업데이트
            meterRegistry.gauge("logpulse.kafka.avg.batch.size", avgBatchSize);
            meterRegistry.gauge("logpulse.kafka.error.rate", errorRate);
        }
    }
}