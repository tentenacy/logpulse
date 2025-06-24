package com.tenacy.logpulse.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "log_archives")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogArchive {
    @Id
    private Long id;

    private String source;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String logLevel;
    private LocalDateTime createdAt;
    private LocalDateTime archivedAt;
}