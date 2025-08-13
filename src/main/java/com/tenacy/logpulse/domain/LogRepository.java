package com.tenacy.logpulse.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LogRepository extends JpaRepository<LogEntry, Long> {
    List<LogEntry> findByLogLevelOrderByCreatedAtDesc(String logLevel, Pageable pageable);

    long countByLogLevelAndCreatedAtBetween(String logLevel, LocalDateTime start, LocalDateTime end);
    long countByLogLevelAndSourceContainingAndCreatedAtBetween(
            String logLevel, String source, LocalDateTime start, LocalDateTime end);
    long countBySourceContainingAndCreatedAtBetween(
            String source, LocalDateTime start, LocalDateTime end);
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Modifying
    @Query("DELETE FROM LogEntry l WHERE l.createdAt < :threshold")
    int deleteLogEntriesOlderThan(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT l.source, COUNT(l) as count " +
            "FROM LogEntry l " +
            "WHERE l.createdAt BETWEEN :startTime AND :endTime " +
            "GROUP BY l.source " +
            "ORDER BY count DESC")
    List<Object[]> findSourceStatsWithTimePeriod(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT l FROM LogEntry l WHERE " +
            "(:keyword IS NULL OR LOWER(l.content) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(l.source) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
            "(:level IS NULL OR l.logLevel = :level) AND " +
            "(:source IS NULL OR LOWER(l.source) LIKE LOWER(CONCAT('%', :source, '%'))) AND " +
            "(:content IS NULL OR LOWER(l.content) LIKE LOWER(CONCAT('%', :content, '%'))) AND " +
            "(:start IS NULL OR :end IS NULL OR l.createdAt BETWEEN :start AND :end)")
    Page<LogEntry> searchWithMultipleCriteria(
            @Param("keyword") String keyword,
            @Param("level") String level,
            @Param("source") String source,
            @Param("content") String content,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);
}