package com.tenacy.logpulse.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LogRepository extends JpaRepository<LogEntry, Long> {
    List<LogEntry> findByLogLevel(String logLevel);
    List<LogEntry> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}