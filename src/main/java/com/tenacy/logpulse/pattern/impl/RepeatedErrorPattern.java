package com.tenacy.logpulse.pattern.impl;

import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.pattern.LogPattern;
import com.tenacy.logpulse.pattern.PatternResult;
import com.tenacy.logpulse.pattern.PatternSeverity;
import com.tenacy.logpulse.pattern.PatternStatus;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 동일한 에러 메시지가 반복적으로 발생하는 패턴을 감지하는 상태 기반 구현
 */
@Component
@Getter
@Setter
public class RepeatedErrorPattern implements LogPattern {

    private final String patternId = "repeated-error";
    private final String name = "반복 에러 패턴 감지";
    private final String description = "동일한 에러가 짧은 시간 내에 반복적으로 발생하는 패턴을 감지합니다.";
    private PatternSeverity severity = PatternSeverity.WARNING;
    private boolean enabled = true;

    // 설정 값
    private int thresholdCount = 3;  // 반복 횟수 임계값
    private long timeWindowMillis = 300000;  // 시간 윈도우 (5분)

    // 상태 데이터 - 스레드 안전성을 위해 ConcurrentHashMap 사용
    private final Map<String, Map<String, List<LogEntry>>> sourceMessageMap = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastDetectionTimeMap = new ConcurrentHashMap<>();

    @Override
    public PatternStatus processLog(LogEntry logEntry) {
        // ERROR 로그만 처리
        if (!"ERROR".equals(logEntry.getLogLevel())) {
            return new PatternStatus(false, null);
        }

        String source = logEntry.getSource();
        String normalizedMessage = normalizeMessage(logEntry.getContent());

        // 오래된 로그 정리
        cleanupOldEntries();

        // 소스별, 메시지별 로그 추적
        Map<String, List<LogEntry>> messageMap = sourceMessageMap.computeIfAbsent(source, k -> new ConcurrentHashMap<>());
        List<LogEntry> logs = messageMap.computeIfAbsent(normalizedMessage, k -> Collections.synchronizedList(new ArrayList<>()));

        // 현재 로그 추가
        logs.add(logEntry);

        // 임계값 초과 확인
        if (logs.size() >= thresholdCount) {
            // 반복 감지 쿨다운 체크 (같은 패턴을 너무 자주 보고하지 않도록)
            String patternKey = source + ":" + normalizedMessage;
            LocalDateTime lastDetection = lastDetectionTimeMap.get(patternKey);
            LocalDateTime now = LocalDateTime.now();

            // 마지막 감지 후 일정 시간(시간 윈도우의 절반)이 지났거나 첫 감지인 경우
            if (lastDetection == null || lastDetection.plusNanos(timeWindowMillis * 500000).isBefore(now)) {
                lastDetectionTimeMap.put(patternKey, now);
                return createDetectionResult(source, normalizedMessage, logs);
            }
        }

        return new PatternStatus(false, null);
    }

    @Override
    public void resetState() {
        sourceMessageMap.clear();
        lastDetectionTimeMap.clear();
    }

    /**
     * 시간 윈도우보다 오래된 로그 항목 제거
     */
    private void cleanupOldEntries() {
        LocalDateTime cutoff = LocalDateTime.now().minusNanos(timeWindowMillis * 1000000);

        sourceMessageMap.forEach((source, messageMap) -> {
            messageMap.forEach((message, logs) -> {
                synchronized (logs) {
                    logs.removeIf(log -> log.getCreatedAt().isBefore(cutoff));
                }
            });

            // 빈 메시지 맵 제거
            messageMap.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        });

        // 빈 소스 맵 제거
        sourceMessageMap.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        // 오래된 감지 시간 제거
        lastDetectionTimeMap.entrySet().removeIf(entry ->
                entry.getValue().plusNanos(timeWindowMillis * 1000000).isBefore(LocalDateTime.now()));
    }

    /**
     * 메시지 정규화 - 비슷한 에러 메시지를 그룹화하기 위함
     */
    private String normalizeMessage(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        // 간단한 정규화: 첫 30자를 사용
        // 실제 구현에서는 더 정교한 정규화 알고리즘을 적용할 수 있음
        // 예: 동적 파라미터 제거, 에러 코드 추출 등
        return message.length() > 30 ? message.substring(0, 30) : message;
    }

    /**
     * 패턴 감지 결과 생성
     */
    private PatternStatus createDetectionResult(String source, String normalizedMessage, List<LogEntry> logs) {
        // 최신 순으로 정렬
        List<LogEntry> sortedLogs = new ArrayList<>(logs);
        sortedLogs.sort(Comparator.comparing(LogEntry::getCreatedAt).reversed());

        // 최대 10개만 포함
        List<LogEntry> recentLogs = sortedLogs.stream()
                .limit(10)
                .collect(Collectors.toList());

        String originalMessage = logs.get(logs.size() - 1).getContent();

        PatternResult result = PatternResult.builder()
                .patternId(patternId)
                .patternName(name)
                .detected(true)
                .severity(severity)
                .message(String.format(
                        "반복 에러 패턴이 감지되었습니다: 소스 '%s'에서 %d초 내에 %d회 유사한 에러가 발생했습니다. 최근 에러: %s",
                        source, timeWindowMillis / 1000, logs.size(), originalMessage))
                .detectedAt(LocalDateTime.now())
                .triggerLog(logs.get(logs.size() - 1))  // 가장 최근 로그를 트리거로 설정
                .relatedLogs(recentLogs)
                .additionalData(Map.of(
                        "source", source,
                        "messagePattern", normalizedMessage,
                        "repeatCount", logs.size(),
                        "timeWindowSeconds", timeWindowMillis / 1000
                ))
                .build();

        return new PatternStatus(true, result);
    }
}