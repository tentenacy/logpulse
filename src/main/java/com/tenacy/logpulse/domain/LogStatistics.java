package com.tenacy.logpulse.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "log_statistics", indexes = {
        @Index(name = "idx_log_stats_date", columnList = "logDate"),
        @Index(name = "idx_log_stats_hour", columnList = "hour"),
        @Index(name = "idx_log_stats_source_level", columnList = "source,logLevel")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogStatistics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate logDate;    // 날짜
    private Integer hour;         // 시간 (0-23)
    private String source;        // 로그 소스
    private String logLevel;      // 로그 레벨
    private Integer count;        // 로그 수

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}