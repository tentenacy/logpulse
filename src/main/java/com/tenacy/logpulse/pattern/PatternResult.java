package com.tenacy.logpulse.pattern;

import com.tenacy.logpulse.domain.LogEntry;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PatternResult {
    private String patternId;
    private String patternName;
    private boolean detected;
    private PatternSeverity severity;
    private String message;
    private LocalDateTime detectedAt;
    private LogEntry triggerLog;
    private List<LogEntry> relatedLogs;
    private Object additionalData;
}