package com.tenacy.logpulse.service;

import com.tenacy.logpulse.domain.LogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Service
@Slf4j
public class JdbcBatchInsertService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${logpulse.jdbc.batch-size:500}")
    private int jdbcBatchSize;

    public JdbcBatchInsertService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void batchInsert(List<LogEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO logs (source, content, log_level, created_at, compressed, original_size, compressed_size) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try {
            // 배치 크기가 너무 큰 경우 여러 배치로 나눔
            if (entries.size() > jdbcBatchSize) {
                int batches = (entries.size() + jdbcBatchSize - 1) / jdbcBatchSize;
                log.debug("{}개 항목의 대규모 배치를 {}개 소규모 배치로 분할", entries.size(), batches);

                for (int i = 0; i < batches; i++) {
                    int fromIndex = i * jdbcBatchSize;
                    int toIndex = Math.min(fromIndex + jdbcBatchSize, entries.size());
                    List<LogEntry> subBatch = entries.subList(fromIndex, toIndex);

                    int[] results = executeJdbcBatch(subBatch, sql);
                    int batchProcessed = countProcessedRows(results);

                    log.debug("서브배치 {}/{} 처리: {}개 행", i+1, batches, batchProcessed);
                }

                log.info("배치 분할 완료: {}개 배치에 {}개 항목", batches, entries.size());
            } else {
                // 단일 배치 실행
                int[] results = executeJdbcBatch(entries, sql);
                int processed = countProcessedRows(results);
                log.debug("단일 배치 처리 완료: {}개 행", processed);
            }
        } catch (Exception e) {
            log.error("배치 삽입 실행 실패: {}", e.getMessage(), e);
            throw new RuntimeException("배치 삽입 실패", e);
        }
    }

    private int[] executeJdbcBatch(List<LogEntry> entries, String sql) {
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
}