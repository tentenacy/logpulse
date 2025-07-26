package com.tenacy.logpulse.service;

import com.tenacy.logpulse.domain.LogEntry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class JdbcBatchInsertService {

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    private final Queue<LogEntry> batchQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean batchProcessingInProgress = new AtomicBoolean(false);

    private final Counter totalInsertedCounter;
    private final Counter batchInsertErrorCounter;
    private final Counter batchExecutionCounter;

    private final AtomicInteger queueSizeGauge = new AtomicInteger(0);
    private final AtomicLong lastBatchTimeGauge = new AtomicLong(0);
    private final AtomicLong totalRowsInsertedGauge = new AtomicLong(0);
    private final AtomicInteger avgBatchSizeGauge = new AtomicInteger(0);

    @Value("${logpulse.jdbc.batch-size:500}")
    private int jdbcBatchSize;

    @Value("${logpulse.jdbc.queue-threshold:1000}")
    private int queueThreshold;

    @Value("${logpulse.jdbc.enable-async:true}")
    private boolean enableAsyncBatch;

    public JdbcBatchInsertService(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.meterRegistry = meterRegistry;

        // 메트릭 등록
        this.totalInsertedCounter = Counter.builder("logpulse.jdbc.rows.inserted")
                .description("Total number of rows inserted into database")
                .register(meterRegistry);

        this.batchInsertErrorCounter = Counter.builder("logpulse.jdbc.errors")
                .description("Number of batch insert errors")
                .register(meterRegistry);

        this.batchExecutionCounter = Counter.builder("logpulse.jdbc.batch.executions")
                .description("Number of batch executions")
                .register(meterRegistry);

        // 게이지 등록
        meterRegistry.gauge("logpulse.jdbc.queue.size", queueSizeGauge);
        meterRegistry.gauge("logpulse.jdbc.batch.time", lastBatchTimeGauge);
        meterRegistry.gauge("logpulse.jdbc.rows.total", totalRowsInsertedGauge);
        meterRegistry.gauge("logpulse.jdbc.batch.avg.size", avgBatchSizeGauge);
    }

    @Transactional
    public void batchInsert(List<LogEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        // 비동기 배치 처리가 활성화되고, 큐 임계값보다 큐 크기가 작고, 배치 크기가 임계값보다 큰 경우
        if (enableAsyncBatch && batchQueue.size() < queueThreshold && entries.size() > jdbcBatchSize) {
            // 비동기 큐에 추가
            batchQueue.addAll(entries);
            int newSize = queueSizeGauge.addAndGet(entries.size());

            log.debug("Added {} entries to async batch queue. Queue size: {}", entries.size(), newSize);

            // 큐 처리가 진행 중이 아니면 처리 시작
            if (batchProcessingInProgress.compareAndSet(false, true)) {
                processBatchQueue();
            }
            return;
        }

        // 동기 처리
        executeBatchInsert(entries);
    }

    private void executeBatchInsert(List<LogEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO logs (source, content, log_level, created_at, compressed, original_size, compressed_size) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        long startTime = System.currentTimeMillis();
        Timer.Sample batchTimer = Timer.start(meterRegistry);

        try {
            // 배치 크기가 너무 큰 경우 여러 배치로 나눔
            if (entries.size() > jdbcBatchSize) {
                int batches = (entries.size() + jdbcBatchSize - 1) / jdbcBatchSize;
                log.debug("Splitting large batch of {} entries into {} smaller batches", entries.size(), batches);

                int totalProcessed = 0;

                for (int i = 0; i < batches; i++) {
                    int fromIndex = i * jdbcBatchSize;
                    int toIndex = Math.min(fromIndex + jdbcBatchSize, entries.size());
                    List<LogEntry> subBatch = entries.subList(fromIndex, toIndex);

                    int[] results = executeJdbcBatch(subBatch, sql);
                    int batchProcessed = countProcessedRows(results);
                    totalProcessed += batchProcessed;

                    log.debug("Processed sub-batch {}/{}: {} rows", i+1, batches, batchProcessed);
                }

                // 평균 배치 크기 업데이트
                avgBatchSizeGauge.set(entries.size() / batches);

                log.info("Split batch completed: {} entries in {} batches", totalProcessed, batches);

                // 카운터 증가 및 게이지 업데이트
                totalInsertedCounter.increment(totalProcessed);
                totalRowsInsertedGauge.addAndGet(totalProcessed);

                // 배치 타이머 기록
                batchTimer.stop(meterRegistry.timer("logpulse.jdbc.batch.time",
                        "type", "split",
                        "size", Integer.toString(entries.size())));
            } else {
                // 단일 배치 실행
                int[] results = executeJdbcBatch(entries, sql);
                int processed = countProcessedRows(results);

                // 카운터 증가 및 게이지 업데이트
                totalInsertedCounter.increment(processed);
                totalRowsInsertedGauge.addAndGet(processed);
                avgBatchSizeGauge.set(entries.size());

                // 배치 타이머 기록
                batchTimer.stop(meterRegistry.timer("logpulse.jdbc.batch.time",
                        "type", "single",
                        "size", Integer.toString(entries.size())));
            }

            long elapsedTime = System.currentTimeMillis() - startTime;
            lastBatchTimeGauge.set(elapsedTime);

            // 처리 성능 측정
            if (entries.size() > 0 && elapsedTime > 0) {
                double rowsPerSecond = entries.size() * 1000.0 / elapsedTime;
                meterRegistry.gauge("logpulse.jdbc.rows.per.second", rowsPerSecond);
            }

        } catch (Exception e) {
            batchInsertErrorCounter.increment();
            log.error("Failed to execute batch insert: {}", e.getMessage(), e);

            // 오류 타이머 기록
            batchTimer.stop(meterRegistry.timer("logpulse.jdbc.batch.time",
                    "type", "error",
                    "size", Integer.toString(entries.size())));

            // 예외를 다시 던져서 트랜잭션이 롤백되도록 함
            throw new RuntimeException("Batch insert failed", e);
        }
    }

    private int[] executeJdbcBatch(List<LogEntry> entries, String sql) {
        batchExecutionCounter.increment();

        return jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                LogEntry entry = entries.get(i);
                ps.setString(1, entry.getSource());
                ps.setString(2, entry.getContent());
                ps.setString(3, entry.getLogLevel());
                ps.setTimestamp(4, Timestamp.valueOf(entry.getCreatedAt()));
                ps.setBoolean(5, entry.getCompressed() != null ? entry.getCompressed() : false);
                ps.setInt(6, entry.getOriginalSize() != null ? entry.getOriginalSize() : 0);
                ps.setInt(7, entry.getCompressedSize() != null ? entry.getCompressedSize() : 0);
            }

            @Override
            public int getBatchSize() {
                return entries.size();
            }
        });
    }

    private int countProcessedRows(int[] results) {
        int count = 0;
        for (int result : results) {
            if (result > 0) {
                count += result;
            }
        }
        return count;
    }

    @Async("jdbcBatchExecutor")
    public void processBatchQueue() {
        if (batchQueue.isEmpty()) {
            batchProcessingInProgress.set(false);
            return;
        }

        Timer.Sample queueProcessingTimer = Timer.start(meterRegistry);
        long startTime = System.currentTimeMillis();

        try {
            List<LogEntry> batch = new ArrayList<>(jdbcBatchSize);

            // 큐에서 배치 크기만큼 항목 추출
            while (!batchQueue.isEmpty() && batch.size() < jdbcBatchSize) {
                LogEntry entry = batchQueue.poll();
                if (entry != null) {
                    batch.add(entry);
                }
            }

            if (!batch.isEmpty()) {
                // 트랜잭션 내에서 배치 실행
                executeBatchInsert(batch);

                // 큐 크기 업데이트
                queueSizeGauge.set(batchQueue.size());

                log.debug("Processed {} entries from batch queue. Remaining: {}",
                        batch.size(), batchQueue.size());

                // 큐에 더 많은 항목이 있으면 다시 처리
                if (!batchQueue.isEmpty()) {
                    processBatchQueue();
                    return;
                }
            }

            // 모든 항목을 처리했거나 큐가 비어 있으면 완료
            batchProcessingInProgress.set(false);

            queueProcessingTimer.stop(meterRegistry.timer("logpulse.jdbc.queue.processing.time"));

        } catch (Exception e) {
            log.error("Error processing batch queue: {}", e.getMessage(), e);

            // 오류가 발생해도 처리 플래그는 해제하고 큐는 계속 유지
            batchProcessingInProgress.set(false);

            // 오류 카운터 증가
            meterRegistry.counter("logpulse.jdbc.errors", "type", "queue_processing").increment();

            queueProcessingTimer.stop(meterRegistry.timer("logpulse.jdbc.queue.processing.time",
                    "result", "error"));
        }
    }

    @Scheduled(fixedRate = 15000)
    public void scheduleBatchQueueProcessing() {
        if (!batchQueue.isEmpty() && !batchProcessingInProgress.get()) {
            log.debug("Scheduled processing of batch queue. Size: {}", batchQueue.size());
            if (batchProcessingInProgress.compareAndSet(false, true)) {
                processBatchQueue();
            }
        }
    }

    @Scheduled(fixedRate = 60000)
    public void logJdbcBatchStats() {
        double batchExecutions = batchExecutionCounter.count();
        double rowsInserted = totalInsertedCounter.count();
        double errors = batchInsertErrorCounter.count();
        int queueSize = queueSizeGauge.get();

        if (batchExecutions > 0) {
            double avgBatchSize = rowsInserted / batchExecutions;
            double errorRate = batchExecutions > 0 ? (errors / batchExecutions) * 100.0 : 0;

            log.info("JDBC Batch stats - Executions: {}, Rows inserted: {}, Avg batch size: {:.1f}, " +
                            "Errors: {} ({:.2f}%), Queue size: {}",
                    (long)batchExecutions,
                    (long)rowsInserted,
                    avgBatchSize,
                    (long)errors,
                    errorRate,
                    queueSize);

            // 통계 게이지 업데이트
            meterRegistry.gauge("logpulse.jdbc.batch.avg.rows", avgBatchSize);
            meterRegistry.gauge("logpulse.jdbc.error.rate", errorRate);
        }
    }
}