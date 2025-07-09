package com.tenacy.logpulse.pattern;

import com.tenacy.logpulse.domain.LogEntry;
import java.util.List;

public interface LogPattern {

    String getPatternId();

    String getName();

    String getDescription();

    boolean detect(LogEntry logEntry);

    boolean detectBatch(List<LogEntry> logEntries);

    PatternResult onDetected(LogEntry logEntry, List<LogEntry> logEntries);

    PatternSeverity getSeverity();

    boolean isEnabled();
}