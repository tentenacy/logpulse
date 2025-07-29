package com.tenacy.logpulse.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@Slf4j
public class LogStatisticsScheduler {

    private final JdbcTemplate jdbcTemplate;

    public LogStatisticsScheduler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 매시간 실행
    @Scheduled(cron = "0 1 * * * *")
    @Transactional
    public void updateHourlyStatistics() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime hourStart = now.minusHours(1).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime hourEnd = hourStart.plusHours(1);

        log.info("시간별 통계 업데이트 시작: {}", hourStart);

        try {
            // 시간별 로그 레벨 통계
            jdbcTemplate.update(
                    "INSERT INTO log_hourly_stats (log_date, hour_of_day, log_level, source, count) " +
                            "SELECT DATE(created_at), HOUR(created_at), log_level, source, COUNT(*) " +
                            "FROM logs " +
                            "WHERE created_at >= ? AND created_at < ? " +
                            "GROUP BY DATE(created_at), HOUR(created_at), log_level, source " +
                            "ON DUPLICATE KEY UPDATE count = VALUES(count)",
                    hourStart, hourEnd
            );

            log.info("시간별 통계 업데이트 완료: {}", hourStart);
        } catch (Exception e) {
            log.error("시간별 통계 업데이트 실패: {}", e.getMessage(), e);
        }
    }

    // 매일 자정 1분 후 실행
    @Scheduled(cron = "0 1 0 * * *")
    @Transactional
    public void updateDailyStatistics() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime dayStart = yesterday.atStartOfDay();
        LocalDateTime dayEnd = yesterday.plusDays(1).atStartOfDay();

        log.info("일별 통계 업데이트 시작: {}", yesterday);

        try {
            // 일별 로그 레벨 통계
            jdbcTemplate.update(
                    "INSERT INTO log_daily_stats (log_date, log_level, source, count) " +
                            "SELECT DATE(created_at), log_level, source, COUNT(*) " +
                            "FROM logs " +
                            "WHERE created_at >= ? AND created_at < ? " +
                            "GROUP BY DATE(created_at), log_level, source " +
                            "ON DUPLICATE KEY UPDATE count = VALUES(count)",
                    dayStart, dayEnd
            );

            // 소스별 통계
            jdbcTemplate.update(
                    "INSERT INTO log_source_stats (log_date, source, log_level, count) " +
                            "SELECT DATE(created_at), source, log_level, COUNT(*) " +
                            "FROM logs " +
                            "WHERE created_at >= ? AND created_at < ? " +
                            "GROUP BY DATE(created_at), source, log_level " +
                            "ON DUPLICATE KEY UPDATE count = VALUES(count)",
                    dayStart, dayEnd
            );

            log.info("일별 통계 업데이트 완료: {}", yesterday);
        } catch (Exception e) {
            log.error("일별 통계 업데이트 실패: {}", e.getMessage(), e);
        }
    }

    // 최초 실행시 또는 수동 요청시 과거 데이터 일괄 처리
    @Transactional
    public void rebuildStatistics(LocalDate startDate, LocalDate endDate) {
        log.info("통계 데이터 재구축 시작: {} ~ {}", startDate, endDate);

        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            LocalDateTime dayStart = current.atStartOfDay();
            LocalDateTime dayEnd = current.plusDays(1).atStartOfDay();

            // 일별 처리
            try {
                // 해당 날짜의 기존 통계 삭제
                jdbcTemplate.update("DELETE FROM log_hourly_stats WHERE log_date = ?", current);
                jdbcTemplate.update("DELETE FROM log_daily_stats WHERE log_date = ?", current);
                jdbcTemplate.update("DELETE FROM log_source_stats WHERE log_date = ?", current);

                // 시간별 통계 생성
                jdbcTemplate.update(
                        "INSERT INTO log_hourly_stats (log_date, hour_of_day, log_level, source, count) " +
                                "SELECT DATE(created_at), HOUR(created_at), log_level, source, COUNT(*) " +
                                "FROM logs " +
                                "WHERE created_at >= ? AND created_at < ? " +
                                "GROUP BY DATE(created_at), HOUR(created_at), log_level, source",
                        dayStart, dayEnd
                );

                // 일별 통계 생성
                jdbcTemplate.update(
                        "INSERT INTO log_daily_stats (log_date, log_level, source, count) " +
                                "SELECT DATE(created_at), log_level, source, COUNT(*) " +
                                "FROM logs " +
                                "WHERE created_at >= ? AND created_at < ? " +
                                "GROUP BY DATE(created_at), log_level, source",
                        dayStart, dayEnd
                );

                // 소스별 통계 생성
                jdbcTemplate.update(
                        "INSERT INTO log_source_stats (log_date, source, log_level, count) " +
                                "SELECT DATE(created_at), source, log_level, COUNT(*) " +
                                "FROM logs " +
                                "WHERE created_at >= ? AND created_at < ? " +
                                "GROUP BY DATE(created_at), source, log_level",
                        dayStart, dayEnd
                );

                log.info("날짜 {} 통계 재구축 완료", current);
            } catch (Exception e) {
                log.error("날짜 {} 통계 재구축 실패: {}", current, e.getMessage(), e);
            }

            current = current.plusDays(1);
        }

        log.info("통계 데이터 재구축 완료: {} ~ {}", startDate, endDate);
    }
}