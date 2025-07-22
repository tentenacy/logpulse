package com.tenacy.logpulse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompressionStatsService {

    private final JdbcTemplate jdbcTemplate;

    private long totalLogsProcessed = 0;
    private long compressedLogsCount = 0;
    private long totalOriginalSize = 0;
    private long totalCompressedSize = 0;

    public void recordCompression(boolean compressed, int originalSize, int compressedSize) {
        totalLogsProcessed++;

        if (compressed) {
            compressedLogsCount++;
            totalOriginalSize += originalSize;
            totalCompressedSize += compressedSize;

            if (compressedLogsCount % 100 == 0) {
                logCompressionStats();
            }
        }
    }

    private void logCompressionStats() {
        if (totalOriginalSize == 0) {
            return;
        }

        double compressionRatio = (double) totalCompressedSize / totalOriginalSize;
        double spaceSaved = 100 * (1 - compressionRatio);

        log.info("Compression stats - Total logs: {}, Compressed: {}, Original size: {} bytes, " +
                        "Compressed size: {} bytes, Space saved: {:.2f}%, Ratio: {:.2f}",
                totalLogsProcessed, compressedLogsCount, totalOriginalSize, totalCompressedSize,
                spaceSaved, compressionRatio);
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void dailyCompressionSummary() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        Map<String, Object> stats = getCompressionStatsForDate(yesterday);

        if (stats.isEmpty()) {
            return;
        }

        log.info("Daily compression summary for {} - Total logs: {}, Compressed logs: {}, " +
                        "Original size: {} MB, Compressed size: {} MB, Space saved: {} MB, Compression ratio: {}%",
                stats.get("date"),
                stats.get("totalLogs"),
                stats.get("compressedLogs"),
                formatMB((Long) stats.get("originalSize")),
                formatMB((Long) stats.get("compressedSize")),
                formatMB((Long) stats.get("originalSize") - (Long) stats.get("compressedSize")),
                stats.get("compressionRatio"));
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
            log.error("Error retrieving compression stats for date {}: {}", dateStr, e.getMessage());
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
            log.error("Error retrieving compression stats for date range {} to {}: {}",
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
            log.error("Error retrieving overall compression stats: {}", e.getMessage());

            // 메모리 통계 사용
            stats.put("totalLogs", totalLogsProcessed);
            stats.put("compressedLogs", compressedLogsCount);
            stats.put("originalSize", totalOriginalSize);
            stats.put("compressedSize", totalCompressedSize);
            stats.put("spaceSaved", totalOriginalSize - totalCompressedSize);
            stats.put("compressionRatio", totalOriginalSize > 0 ?
                    (1 - (double)totalCompressedSize/totalOriginalSize) * 100 : 0);
        }

        return stats;
    }

    private double formatMB(long bytes) {
        return (double) bytes / (1024 * 1024);
    }
}