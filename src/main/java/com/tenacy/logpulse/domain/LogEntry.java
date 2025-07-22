package com.tenacy.logpulse.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "logs", indexes = {
        @Index(name = "idx_logs_compressed", columnList = "compressed"),
        @Index(name = "idx_logs_log_level", columnList = "logLevel"),
        @Index(name = "idx_logs_source", columnList = "source"),
        @Index(name = "idx_logs_created_at", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String source;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String logLevel;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "compressed")
    private Boolean compressed;

    @Column(name = "original_size")
    private Integer originalSize;

    @Column(name = "compressed_size")
    private Integer compressedSize;

    @Transient
    public String getDecompressedContent() {
        return this.content;
    }
}