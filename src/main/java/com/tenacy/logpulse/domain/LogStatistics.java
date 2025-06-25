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
@Table(name = "log_statistics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogStatistics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate logDate;
    private String source;
    private String logLevel;
    private Integer count;

    @CreationTimestamp
    private LocalDateTime createdAt;
}