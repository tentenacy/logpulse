package com.tenacy.logpulse.pattern;

import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.service.AlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class LogPatternDetector {

    private final Map<String, LogPattern> patterns = new ConcurrentHashMap<>();

    private final AlertService alertService;

    @Autowired
    public LogPatternDetector(AlertService alertService, List<LogPattern> logPatterns) {
        this.alertService = alertService;

        for (LogPattern pattern : logPatterns) {
            registerPattern(pattern);
        }
    }

    public void registerPattern(LogPattern pattern) {
        patterns.put(pattern.getPatternId(), pattern);
    }

    public void unregisterPattern(String patternId) {
        LogPattern removed = patterns.remove(patternId);
    }

    public List<PatternResult> detectPatterns(LogEntry logEntry) {
        List<PatternResult> results = new ArrayList<>();

        for (LogPattern pattern : patterns.values()) {
            if (pattern.isEnabled() && pattern.detect(logEntry)) {
                PatternResult result = pattern.onDetected(logEntry, null);
                results.add(result);

                // 알림 서비스 호출
                if (result.getSeverity().ordinal() >= PatternSeverity.WARNING.ordinal()) {
                    alertService.sendAlert(
                            "LogPulse 패턴 감지: " + pattern.getName(),
                            result.getMessage()
                    );
                }
            }
        }

        return results;
    }

    public List<PatternResult> detectBatchPatterns(List<LogEntry> logEntries) {
        List<PatternResult> results = new ArrayList<>();

        for (LogPattern pattern : patterns.values()) {
            if (pattern.isEnabled() && pattern.detectBatch(logEntries)) {
                PatternResult result = pattern.onDetected(null, logEntries);
                results.add(result);

                // 알림 서비스 호출
                if (result.getSeverity().ordinal() >= PatternSeverity.WARNING.ordinal()) {
                    alertService.sendAlert(
                            "LogPulse 배치 패턴 감지: " + pattern.getName(),
                            result.getMessage()
                    );
                }
            }
        }

        return results;
    }

    public List<LogPattern> getAllPatterns() {
        return new ArrayList<>(patterns.values());
    }

    public LogPattern getPattern(String patternId) {
        return patterns.get(patternId);
    }
}