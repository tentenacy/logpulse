package com.tenacy.logpulse.pattern;

import com.tenacy.logpulse.domain.LogEntry;
import java.util.List;

public interface LogPattern {
    String getPatternId();
    String getName();
    String getDescription();
    PatternSeverity getSeverity();
    boolean isEnabled();
    PatternStatus processLog(LogEntry logEntry);
    void resetState();
}