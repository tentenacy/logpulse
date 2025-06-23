package com.tenacy.logpulse.api.dto;

import com.tenacy.logpulse.domain.LogEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEntryResponse {
    private Long id;
    private String source;
    private String content;
    private String logLevel;
    private LocalDateTime createdAt;

    public static LogEntryResponse of(LogEntry logEntry) {
        return LogEntryResponse.builder()
                .id(logEntry.getId())
                .source(logEntry.getSource())
                .content(logEntry.getContent())
                .logLevel(logEntry.getLogLevel())
                .createdAt(logEntry.getCreatedAt())
                .build();
    }
}