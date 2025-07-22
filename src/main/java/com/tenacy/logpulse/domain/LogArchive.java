package com.tenacy.logpulse.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "log_archives", indexes = {
        @Index(name = "idx_log_archives_compressed", columnList = "compressed"),
        @Index(name = "idx_log_archives_log_level", columnList = "logLevel"),
        @Index(name = "idx_log_archives_source", columnList = "source"),
        @Index(name = "idx_log_archives_created_at", columnList = "createdAt"),
        @Index(name = "idx_log_archives_archived_at", columnList = "archivedAt")
})
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

    @Column(name = "compressed")
    private boolean compressed;

    @Column(name = "original_size")
    private Integer originalSize;

    @Column(name = "compressed_size")
    private Integer compressedSize;
}