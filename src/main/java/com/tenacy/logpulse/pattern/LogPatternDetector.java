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
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class LogPatternDetector {

    private final List<LogPattern> patterns = new CopyOnWriteArrayList<>();
    private final AlertService alertService;

    public LogPatternDetector(AlertService alertService, List<LogPattern> patternList) {
        this.alertService = alertService;

        if (patternList != null) {
            patterns.addAll(patternList);
            log.info("Initialized LogPatternDetector with {} patterns", patterns.size());
        }
    }

    /**
     * 새로운 로그 항목 처리 및 패턴 감지
     */
    public List<PatternResult> processLog(LogEntry logEntry) {
        List<PatternResult> detectedPatterns = new ArrayList<>();

        for (LogPattern pattern : patterns) {
            if (!pattern.isEnabled()) {
                continue;
            }

            try {
                PatternStatus status = pattern.processLog(logEntry);

                if (status.isDetected() && status.getResult() != null) {
                    detectedPatterns.add(status.getResult());

                    // 심각도에 따라 알림 발송
                    if (status.getResult().getSeverity().ordinal() >= PatternSeverity.WARNING.ordinal()) {
                        sendAlert(status.getResult());
                    }
                }
            } catch (Exception e) {
                log.error("Error processing log with pattern {}: {}", pattern.getPatternId(), e.getMessage(), e);
            }
        }

        return detectedPatterns;
    }

    /**
     * 패턴 감지 시 알림 발송
     */
    private void sendAlert(PatternResult result) {
        try {
            String subject = "LogPulse 패턴 감지: " + result.getPatternName();
            alertService.sendAlert(subject, result.getMessage());
        } catch (Exception e) {
            log.error("Failed to send alert for pattern {}: {}", result.getPatternId(), e.getMessage(), e);
        }
    }

    /**
     * 패턴 등록
     */
    public void registerPattern(LogPattern pattern) {
        patterns.add(pattern);
        log.info("Registered stateful pattern: {}", pattern.getPatternId());
    }

    /**
     * 패턴 제거
     */
    public void unregisterPattern(String patternId) {
        patterns.removeIf(p -> p.getPatternId().equals(patternId));
        log.info("Unregistered stateful pattern: {}", patternId);
    }

    /**
     * 모든 패턴 상태 리셋
     */
    public void resetAllPatterns() {
        patterns.forEach(LogPattern::resetState);
        log.info("Reset state for all stateful patterns");
    }

    /**
     * 특정 패턴 상태 리셋
     */
    public void resetPattern(String patternId) {
        patterns.stream()
                .filter(p -> p.getPatternId().equals(patternId))
                .forEach(LogPattern::resetState);
        log.info("Reset state for pattern: {}", patternId);
    }
}