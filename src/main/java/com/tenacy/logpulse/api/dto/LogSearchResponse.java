package com.tenacy.logpulse.api.dto;

import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.elasticsearch.document.LogDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogSearchResponse {
    private String id;
    private String source;
    private String content;
    private String logLevel;
    private LocalDateTime timestamp;

    public static Page<LogSearchResponse> pageOf(Page<LogEntry> logEntries) {
        return logEntries.map(entry ->
                LogSearchResponse.builder()
                        .id(entry.getId().toString())
                        .source(entry.getSource())
                        .content(entry.getContent())
                        .logLevel(entry.getLogLevel())
                        .timestamp(entry.getCreatedAt())
                        .build());
    }

    public static Page<LogSearchResponse> pageOfLogEntryResponse(Page<LogEntryResponse> logEntries) {
        return logEntries.map(entry ->
                LogSearchResponse.builder()
                        .id(entry.getId().toString())
                        .source(entry.getSource())
                        .content(entry.getContent())
                        .logLevel(entry.getLogLevel())
                        .timestamp(entry.getCreatedAt())
                        .build());
    }

    public static List<LogSearchResponse> listOf(List<LogDocument> logs) {
        return logs.stream()
                .map(log -> LogSearchResponse.builder()
                        .id(log.getId())
                        .source(log.getSource())
                        .content(log.getContent())
                        .logLevel(log.getLogLevel())
                        .timestamp(log.getTimestamp())
                        .build())
                .collect(Collectors.toList());
    }
}