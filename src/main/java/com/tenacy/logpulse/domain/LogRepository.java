package com.tenacy.logpulse.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LogRepository extends JpaRepository<LogEntry, Long> {
    List<LogEntry> findByLogLevel(String logLevel);
    List<LogEntry> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    List<LogEntry> findBySource(String source);

    @Modifying
    @Query("DELETE FROM LogEntry l WHERE l.createdAt < :threshold")
    int deleteLogEntriesOlderThan(@Param("threshold") LocalDateTime threshold);

}