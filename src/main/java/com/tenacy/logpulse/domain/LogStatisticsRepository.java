package com.tenacy.logpulse.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LogStatisticsRepository extends JpaRepository<LogStatistics, Long> {

    // 특정 날짜의 모든 통계 조회
    List<LogStatistics> findByLogDate(LocalDate date);

    // 특정 날짜 및 시간의 통계 조회
    List<LogStatistics> findByLogDateAndHour(LocalDate date, Integer hour);

    // 특정 날짜, 소스, 레벨, 시간에 해당하는 통계 찾기
    LogStatistics findByLogDateAndSourceAndLogLevelAndHour(
            LocalDate date, String source, String logLevel, Integer hour);

    // 날짜 범위의 소스별 통계 조회
    @Query("SELECT ls.source, SUM(ls.count) FROM LogStatistics ls " +
            "WHERE ls.logDate BETWEEN :startDate AND :endDate " +
            "GROUP BY ls.source ORDER BY SUM(ls.count) DESC")
    List<Object[]> findSourceStatsByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // 날짜 범위의 로그 레벨별 통계 조회
    @Query("SELECT ls.logLevel, SUM(ls.count) FROM LogStatistics ls " +
            "WHERE ls.logDate BETWEEN :startDate AND :endDate " +
            "GROUP BY ls.logLevel")
    List<Object[]> findLevelStatsByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // 날짜별, 시간별 통계 조회
    @Query("SELECT ls.hour, ls.logLevel, SUM(ls.count) FROM LogStatistics ls " +
            "WHERE ls.logDate = :date " +
            "GROUP BY ls.hour, ls.logLevel " +
            "ORDER BY ls.hour")
    List<Object[]> findHourlyStatsByDate(@Param("date") LocalDate date);

    // 통계 레코드 갱신 (count 증가)
    @Modifying
    @Query("UPDATE LogStatistics ls SET ls.count = ls.count + 1, ls.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE ls.logDate = :date AND ls.hour = :hour " +
            "AND ls.source = :source AND ls.logLevel = :logLevel")
    int incrementCount(
            @Param("date") LocalDate date,
            @Param("hour") Integer hour,
            @Param("source") String source,
            @Param("logLevel") String logLevel);

    @Query("SELECT ls.logLevel, SUM(ls.count) FROM LogStatistics ls " +
            "WHERE ls.logDate BETWEEN :startDate AND :endDate " +
            "AND ls.source LIKE %:source% " +
            "GROUP BY ls.logLevel")
    List<Object[]> findLevelStatsByDateRangeAndSource(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("source") String source);
}