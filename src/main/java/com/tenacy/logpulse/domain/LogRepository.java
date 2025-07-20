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
    List<LogEntry> findByLogLevel(String logLevel);
    List<LogEntry> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    List<LogEntry> findBySourceContaining(String source);
    List<LogEntry> findByLogLevelOrderByCreatedAtDesc(String logLevel, Pageable pageable);
    Page<LogEntry> findByLogLevel(String logLevel, Pageable pageable);
    Page<LogEntry> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
    Page<LogEntry> findBySourceContaining(String source, Pageable pageable);
    Page<LogEntry> findByContentContaining(String content, Pageable pageable);
    Page<LogEntry> findByContentContainingIgnoreCaseOrSourceContainingIgnoreCase(
            String content, String source, Pageable pageable);
    Page<LogEntry> findByLogLevelAndSourceContainingAndCreatedAtBetween(String level, String source,
                                                                        LocalDateTime start, LocalDateTime end,
                                                                        Pageable pageable);
    Page<LogEntry> findByLogLevelAndSourceContaining(String level, String source, Pageable pageable);
    Page<LogEntry> findByLogLevelAndCreatedAtBetween(String level, LocalDateTime start, LocalDateTime end,
                                                     Pageable pageable);
    Page<LogEntry> findBySourceContainingAndCreatedAtBetween(String source, LocalDateTime start, LocalDateTime end,
                                                             Pageable pageable);

    long countByCreatedAtAfter(LocalDateTime dateTime);
    long countByLogLevelAndCreatedAtBetween(String logLevel, LocalDateTime start, LocalDateTime end);
    long countByLogLevelAndSourceContainingAndCreatedAtBetween(
            String logLevel, String source, LocalDateTime start, LocalDateTime end);
    long countBySourceContainingAndCreatedAtBetween(
            String source, LocalDateTime start, LocalDateTime end);
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    long countByLogLevelAndCreatedAtAfter(String logLevel, LocalDateTime dateTime);

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
}