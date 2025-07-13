package com.tenacy.logpulse.service;

import com.tenacy.logpulse.api.dto.LogEntryRequest;
import com.tenacy.logpulse.api.dto.LogEntryResponse;
import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.domain.LogRepository;
import com.tenacy.logpulse.elasticsearch.service.ElasticsearchService;
import com.tenacy.logpulse.pattern.LogPatternDetector;
import com.tenacy.logpulse.pattern.PatternResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LogService {

    private final LogRepository logRepository;
    private final ElasticsearchService elasticsearchService;
    private final LogMetricsService logMetricsService;
    private final RealTimeErrorMonitorService errorMonitorService;
    private final LogPatternDetector patternDetector;

    @Transactional
    public LogEntryResponse createLog(LogEntryRequest request) {
        LogEntry logEntry = LogEntry.builder()
                .source(request.getSource())
                .content(request.getContent())
                .logLevel(request.getLogLevel())
                .build();

        LogEntry savedEntry = logRepository.save(logEntry);

        LogEventDto eventDto = LogEventDto.builder()
                .source(savedEntry.getSource())
                .content(savedEntry.getContent())
                .logLevel(savedEntry.getLogLevel())
                .timestamp(savedEntry.getCreatedAt())
                .build();

        try {
            // Elasticsearch 저장
            elasticsearchService.saveLog(savedEntry);
        } catch (Exception e) {
            log.error("Failed to save log to Elasticsearch: {}", e.getMessage(), e);
            // 핵심 저장(DB)은 이미 완료되었으므로 실패해도 계속 진행
        }

        try {
            // 메트릭 수집
            logMetricsService.recordLog(eventDto);
        } catch (Exception e) {
            log.error("Failed to record metrics: {}", e.getMessage(), e);
        }

        try {
            // 실시간 오류 모니터링
            errorMonitorService.monitorLog(eventDto);
        } catch (Exception e) {
            log.error("Failed to monitor errors: {}", e.getMessage(), e);
        }

        // 패턴 감지 수행
        patternDetector.processLog(savedEntry);

        return LogEntryResponse.of(savedEntry);
    }

    public List<LogEntryResponse> retrieveAllLogs() {
        return logRepository.findAll().stream()
                .map(LogEntryResponse::of)
                .collect(Collectors.toList());
    }

    public List<LogEntryResponse> retrieveLogsByLevel(String level) {
        return logRepository.findByLogLevel(level).stream()
                .map(LogEntryResponse::of)
                .collect(Collectors.toList());
    }

    public List<LogEntryResponse> retrieveLogsBetween(LocalDateTime start, LocalDateTime end) {
        return logRepository.findByCreatedAtBetween(start, end).stream()
                .map(LogEntryResponse::of)
                .collect(Collectors.toList());
    }
}
