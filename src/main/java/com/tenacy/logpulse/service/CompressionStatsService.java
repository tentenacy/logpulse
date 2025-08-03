package com.tenacy.logpulse.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class CompressionStatsService {

    private final JdbcTemplate jdbcTemplate;

    private long totalProcessed = 0;
    private long totalCompressed = 0;
    private long totalOriginalSize = 0;
    private long totalCompressedSize = 0;

    public CompressionStatsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordCompression(boolean compressed, int originalSize, int compressedSize) {
        totalProcessed++;
        totalOriginalSize += originalSize;
        totalCompressedSize += compressedSize;

        if (compressed) {
            totalCompressed++;
        }
    }

    public Map<String, Object> getCompressionStatsForDate(LocalDate date) {
        String dateStr = date.format(DateTimeFormatter.ISO_DATE);

        try {
            String sql = "SELECT * FROM v_log_compression_stats WHERE log_date = ?";

            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, dateStr);

            if (!results.isEmpty()) {
                return results.get(0);
            }
        } catch (Exception e) {
            log.error("날짜 {}에 대한 압축 통계를 검색하는 중 오류 발생: {}", dateStr, e.getMessage());
        }

        return new HashMap<>();
    }

    public List<Map<String, Object>> getCompressionStatsForDateRange(LocalDate startDate, LocalDate endDate) {
        String startDateStr = startDate.format(DateTimeFormatter.ISO_DATE);
        String endDateStr = endDate.format(DateTimeFormatter.ISO_DATE);

        try {
            String sql = "SELECT * FROM v_log_compression_stats WHERE log_date BETWEEN ? AND ? ORDER BY log_date";

            return jdbcTemplate.queryForList(sql, startDateStr, endDateStr);
        } catch (Exception e) {
            log.error("날짜 범위 {} ~ {}에 대한 압축 통계를 검색하는 중 오류 발생: {}",
                    startDateStr, endDateStr, e.getMessage());

            return List.of();
        }
    }

    public Map<String, Object> getOverallCompressionStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            String sql = "SELECT " +
                    "SUM(total_logs) as total_logs, " +
                    "SUM(compressed_logs) as compressed_logs, " +
                    "SUM(total_original_size) as total_original_size, " +
                    "SUM(total_compressed_size) as total_compressed_size, " +
                    "CASE WHEN SUM(total_original_size) > 0 THEN " +
                    "  (1 - (SUM(total_compressed_size) / SUM(total_original_size))) * 100 " +
                    "ELSE 0 END as overall_compression_ratio " +
                    "FROM v_log_compression_stats";

            Map<String, Object> result = jdbcTemplate.queryForMap(sql);

            stats.put("totalLogs", result.get("total_logs"));
            stats.put("compressedLogs", result.get("compressed_logs"));
            stats.put("originalSize", result.get("total_original_size"));
            stats.put("compressedSize", result.get("total_compressed_size"));
            stats.put("spaceSaved", (Long)result.get("total_original_size") - (Long)result.get("total_compressed_size"));
            stats.put("compressionRatio", result.get("overall_compression_ratio"));

        } catch (Exception e) {
            log.error("전체 압축 통계를 검색하는 중 오류 발생: {}", e.getMessage());

            // 메모리 통계 사용
            stats.put("totalLogs", totalProcessed);
            stats.put("compressedLogs", totalCompressed);
            stats.put("originalSize", totalOriginalSize);
            stats.put("compressedSize", totalCompressedSize);
            stats.put("spaceSaved", totalOriginalSize - totalCompressedSize);

            double ratio = totalOriginalSize > 0 ?
                    (1 - ((double)totalCompressedSize / totalOriginalSize)) * 100 : 0;
            stats.put("compressionRatio", ratio);
        }

        return stats;
    }
}