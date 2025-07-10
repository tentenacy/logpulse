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
public class LogEventDto {
    private String source;
    private String content;
    private String logLevel;
    private LocalDateTime timestamp;

    public static LogEventDto of(LogEntry logEntry) {
        return LogEventDto.builder()
                .source(logEntry.getSource())
                .content(logEntry.getContent())
                .logLevel(logEntry.getLogLevel())
                .timestamp(logEntry.getCreatedAt())
                .build();
    }
}