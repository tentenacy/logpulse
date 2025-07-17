package com.tenacy.logpulse.domain;

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
    List<LogEntry> findBySource(String source);
    Long countByLogLevel(String logLevel);
    long countByCreatedAtAfter(LocalDateTime dateTime);

    @Modifying
    @Query("DELETE FROM LogEntry l WHERE l.createdAt < :threshold")
    int deleteLogEntriesOlderThan(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT l.source, COUNT(l) FROM LogEntry l GROUP BY l.source")
    List<Object[]> countBySourceGrouped();

    @Query("SELECT HOUR(l.createdAt), l.logLevel, COUNT(l) FROM LogEntry l " +
            "WHERE DATE(l.createdAt) = :date GROUP BY HOUR(l.createdAt), l.logLevel")
    List<Object[]> countByHourAndLevel(@Param("date") LocalDate date);
}