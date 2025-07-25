package com.tenacy.logpulse.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class CompressionStatsService {

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    // 메트릭 카운터 정의
    private final Counter logsProcessedCounter;
    private final Counter compressedLogsCounter;
    private final Counter originalSizeCounter;
    private final Counter compressedSizeCounter;

    // 게이지 값 추적을 위한 AtomicLong 객체들
    private final AtomicLong compressionRatioValue = new AtomicLong(0);
    private final AtomicLong spaceSavedValue = new AtomicLong(0);

    public CompressionStatsService(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.meterRegistry = meterRegistry;

        // 카운터 등록
        this.logsProcessedCounter = Counter.builder("logpulse.compression.logs.processed")
                .description("Total number of logs processed for compression")
                .register(meterRegistry);

        this.compressedLogsCounter = Counter.builder("logpulse.compression.logs.compressed")
                .description("Number of logs that were actually compressed")
                .register(meterRegistry);

        this.originalSizeCounter = Counter.builder("logpulse.compression.bytes.original")
                .description("Total size of logs before compression in bytes")
                .register(meterRegistry);

        this.compressedSizeCounter = Counter.builder("logpulse.compression.bytes.compressed")
                .description("Total size of logs after compression in bytes")
                .register(meterRegistry);

        // 게이지 등록
        Gauge.builder("logpulse.compression.ratio", compressionRatioValue, AtomicLong::get)
                .description("Current compression ratio (compressed / original)")
                .baseUnit("ratio")
                .register(meterRegistry);

        Gauge.builder("logpulse.compression.space.saved", spaceSavedValue, AtomicLong::get)
                .description("Space saved by compression in bytes")
                .baseUnit("bytes")
                .register(meterRegistry);
    }

    public void recordCompression(boolean compressed, int originalSize, int compressedSize) {
        // 전체 로그 카운터 증가
        logsProcessedCounter.increment();

        // 원본 크기 카운터 증가
        originalSizeCounter.increment(originalSize);

        if (compressed) {
            // 압축된 로그 카운터 증가
            compressedLogsCounter.increment();

            // 압축된 크기 카운터 증가
            compressedSizeCounter.increment(compressedSize);

            // 저장 공간 및 압축률 계산 (100개마다 업데이트)
            if (logsProcessedCounter.count() % 100 == 0) {
                updateCompressionMetrics();
            }
        } else {
            // 압축되지 않은 경우도 동일한 크기로 기록
            compressedSizeCounter.increment(originalSize);
        }
    }

    private void updateCompressionMetrics() {
        double originalTotal = originalSizeCounter.count();
        double compressedTotal = compressedSizeCounter.count();

        if (originalTotal > 0) {
            // 압축률 업데이트
            double ratio = compressedTotal / originalTotal;
            compressionRatioValue.set(Math.round(ratio * 10000) / 10000); // 소수점 4자리까지 저장

            // 저장 공간 업데이트
            long saved = (long)(originalTotal - compressedTotal);
            spaceSavedValue.set(saved);

            // 로그 출력 (디버그 레벨로 변경)
            log.debug("Compression metrics updated - Original: {} bytes, Compressed: {} bytes, Ratio: {}, Saved: {} bytes",
                    (long)originalTotal, (long)compressedTotal, ratio, saved);
        }
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

        // 메트릭에 일간 통계 기록
        meterRegistry.gauge("logpulse.compression.daily.logs.total", (Number)stats.get("totalLogs"));
        meterRegistry.gauge("logpulse.compression.daily.logs.compressed", (Number)stats.get("compressedLogs"));
        meterRegistry.gauge("logpulse.compression.daily.size.original", (Number)stats.get("originalSize"));
        meterRegistry.gauge("logpulse.compression.daily.size.compressed", (Number)stats.get("compressedSize"));
        meterRegistry.gauge("logpulse.compression.daily.ratio", (Number)stats.get("compressionRatio"));
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

            // 에러 카운터 증가
            meterRegistry.counter("logpulse.compression.errors", "type", "db_query").increment();
        }

        return new HashMap<>();
    }

    public List<Map<String, Object>> getCompressionStatsForDateRange(LocalDate startDate, LocalDate endDate) {
        String startDateStr = startDate.format(DateTimeFormatter.ISO_DATE);
        String endDateStr = endDate.format(DateTimeFormatter.ISO_DATE);

        try {
            String sql = "SELECT * FROM v_log_compression_stats WHERE log_date BETWEEN ? AND ? ORDER BY log_date";

            // 쿼리 수행 시간 측정
            Timer.Sample sample = Timer.start(meterRegistry);
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, startDateStr, endDateStr);
            sample.stop(meterRegistry.timer("logpulse.compression.query.time", "type", "date_range"));

            return results;
        } catch (Exception e) {
            log.error("Error retrieving compression stats for date range {} to {}: {}",
                    startDateStr, endDateStr, e.getMessage());

            // 에러 카운터 증가
            meterRegistry.counter("logpulse.compression.errors", "type", "date_range_query").increment();

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

            // 쿼리 수행 시간 측정
            Timer.Sample sample = Timer.start(meterRegistry);
            Map<String, Object> result = jdbcTemplate.queryForMap(sql);
            sample.stop(meterRegistry.timer("logpulse.compression.query.time", "type", "overall"));

            stats.put("totalLogs", result.get("total_logs"));
            stats.put("compressedLogs", result.get("compressed_logs"));
            stats.put("originalSize", result.get("total_original_size"));
            stats.put("compressedSize", result.get("total_compressed_size"));
            stats.put("spaceSaved", (Long)result.get("total_original_size") - (Long)result.get("total_compressed_size"));
            stats.put("compressionRatio", result.get("overall_compression_ratio"));

        } catch (Exception e) {
            log.error("Error retrieving overall compression stats: {}", e.getMessage());

            // 에러 카운터 증가
            meterRegistry.counter("logpulse.compression.errors", "type", "overall_query").increment();

            // 메모리 메트릭 사용
            stats.put("totalLogs", (long)logsProcessedCounter.count());
            stats.put("compressedLogs", (long)compressedLogsCounter.count());
            stats.put("originalSize", (long)originalSizeCounter.count());
            stats.put("compressedSize", (long)compressedSizeCounter.count());
            stats.put("spaceSaved", (long)(originalSizeCounter.count() - compressedSizeCounter.count()));

            double ratio = originalSizeCounter.count() > 0 ?
                    (1 - (compressedSizeCounter.count() / originalSizeCounter.count())) * 100 : 0;
            stats.put("compressionRatio", ratio);
        }

        return stats;
    }

    /**
     * 주기적으로 압축 통계 로깅 (5분마다)
     */
    @Scheduled(fixedRate = 300000)
    public void logCompressionStats() {
        double processedCount = logsProcessedCounter.count();
        double compressedCount = compressedLogsCounter.count();
        double originalSize = originalSizeCounter.count();
        double compressedSize = compressedSizeCounter.count();

        if (processedCount > 0) {
            double compressionRate = (compressedCount / processedCount) * 100.0;
            double compressionRatio = originalSize > 0 ? compressedSize / originalSize : 1.0;
            double spaceSaved = originalSize - compressedSize;

            log.info("Compression stats - Total logs: {}, Compressed: {} ({:.2f}%), " +
                            "Original size: {} MB, Compressed size: {} MB, " +
                            "Space saved: {} MB ({:.2f}%), Ratio: {:.2f}",
                    (long)processedCount,
                    (long)compressedCount,
                    compressionRate,
                    formatMB((long)originalSize),
                    formatMB((long)compressedSize),
                    formatMB((long)spaceSaved),
                    originalSize > 0 ? (spaceSaved / originalSize) * 100 : 0,
                    compressionRatio);

            // 게이지 값 업데이트
            compressionRatioValue.set(Math.round(compressionRatio * 10000) / 10000);
            spaceSavedValue.set((long)spaceSaved);
        }
    }

    private double formatMB(long bytes) {
        return (double) bytes / (1024 * 1024);
    }
}