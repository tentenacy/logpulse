package com.tenacy.logpulse.service;

import com.tenacy.logpulse.domain.LogEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class JdbcBatchInsertService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void batchInsert(List<LogEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO logs (source, content, log_level, created_at, compressed, original_size, compressed_size) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        long startTime = System.currentTimeMillis();

        int[] batchResult = jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
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

        long endTime = System.currentTimeMillis();
        log.info("JDBC Batch inserted {} log entries in {} ms",
                entries.size(), (endTime - startTime));
    }
}