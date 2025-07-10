package com.tenacy.logpulse.pattern.impl;

import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.pattern.AbstractLogPattern;
import com.tenacy.logpulse.pattern.PatternResult;
import com.tenacy.logpulse.pattern.PatternSeverity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ERROR 로그가 짧은 시간 내에 급증하는 패턴 감지
 */
@Component
@Getter
@Setter
public class ErrorSpikePattern extends AbstractLogPattern {

    private int thresholdCount = 5;
    private int timeWindowSeconds = 60;

    public ErrorSpikePattern() {
        super(
                "error-spike",
                "ERROR 로그 급증 감지",
                "짧은 시간 내에 ERROR 로그가 급증하는 패턴을 감지합니다."
        );
        setSeverity(PatternSeverity.WARNING);
    }

    @Override
    public boolean detect(LogEntry logEntry) {
        // 단일 로그로는 판단할 수 없으므로 항상 false 반환
        return false;
    }

    @Override
    public boolean detectBatch(List<LogEntry> logEntries) {
        if (logEntries == null || logEntries.isEmpty()) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusSeconds(timeWindowSeconds);

        // 지정된 시간 범위 내의 ERROR 로그 수 계산
        long errorCount = logEntries.stream()
                .filter(log -> "ERROR".equals(log.getLogLevel()))
                .filter(log -> log.getCreatedAt().isAfter(threshold))
                .count();

        return errorCount >= thresholdCount;
    }

    @Override
    public PatternResult onDetected(LogEntry logEntry, List<LogEntry> logEntries) {
        // ERROR 로그만 필터링
        List<LogEntry> errorLogs = logEntries.stream()
                .filter(log -> "ERROR".equals(log.getLogLevel()))
                .collect(Collectors.toList());

        // 추가 정보를 포함한 결과 생성
        return PatternResult.builder()
                .patternId(getPatternId())
                .patternName(getName())
                .detected(true)
                .severity(getSeverity())
                .message(String.format(
                        "지난 %d초 동안 %d개의 ERROR 로그가 감지되었습니다.",
                        timeWindowSeconds, errorLogs.size()))
                .detectedAt(LocalDateTime.now())
                .relatedLogs(errorLogs)
                .additionalData(Map.of(
                        "timeWindowSeconds", timeWindowSeconds,
                        "thresholdCount", thresholdCount,
                        "actualCount", errorLogs.size()
                ))
                .build();
    }
}