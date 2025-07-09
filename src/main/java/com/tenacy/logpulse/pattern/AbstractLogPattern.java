package com.tenacy.logpulse.pattern;

import com.tenacy.logpulse.domain.LogEntry;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
public abstract class AbstractLogPattern implements LogPattern {

    private String patternId;
    private String name;
    private String description;
    private PatternSeverity severity = PatternSeverity.INFO;
    private boolean enabled = true;

    public AbstractLogPattern(String patternId, String name, String description) {
        this.patternId = patternId;
        this.name = name;
        this.description = description;
    }

    @Override
    public boolean detectBatch(List<LogEntry> logEntries) {
        for (LogEntry logEntry : logEntries) {
            if (detect(logEntry)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public PatternResult onDetected(LogEntry logEntry, List<LogEntry> logEntries) {
        return PatternResult.builder()
                .patternId(patternId)
                .patternName(name)
                .detected(true)
                .severity(severity)
                .message(generateAlertMessage(logEntry))
                .detectedAt(LocalDateTime.now())
                .triggerLog(logEntry)
                .relatedLogs(logEntries != null ? logEntries : Collections.singletonList(logEntry))
                .build();
    }

    protected String generateAlertMessage(LogEntry logEntry) {
        return String.format("[%s] 패턴 감지: %s - %s",
                severity,
                name,
                logEntry != null ? logEntry.getContent() : "관련 로그 없음");
    }
}