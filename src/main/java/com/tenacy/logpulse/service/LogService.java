package com.tenacy.logpulse.service;

import com.tenacy.logpulse.api.dto.LogEntryRequest;
import com.tenacy.logpulse.api.dto.LogEntryResponse;
import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.domain.LogRepository;
import com.tenacy.logpulse.elasticsearch.service.ElasticsearchService;
import com.tenacy.logpulse.pattern.LogPatternDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class LogService {

    private final LogRepository logRepository;
    private final ElasticsearchService elasticsearchService;
    private final LogMetricsService logMetricsService;
    private final RealTimeErrorMonitorService errorMonitorService;
    private final LogPatternDetector patternDetector;

    @Transactional
    public LogEntryResponse createLog(LogEntryRequest request) {
        // LogEntry 생성
        LogEntry logEntry = LogEntry.builder()
                .source(request.getSource())
                .content(request.getContent())
                .logLevel(request.getLogLevel())
                .createdAt(LocalDateTime.now())
                .build();

        // 데이터베이스에 저장
        LogEntry savedEntry = logRepository.save(logEntry);
        log.debug("Log entry saved to database: {}", savedEntry.getId());

        // 비동기 처리를 위한 DTO 생성
        LogEventDto eventDto = LogEventDto.builder()
                .source(savedEntry.getSource())
                .content(savedEntry.getContent())
                .logLevel(savedEntry.getLogLevel())
                .timestamp(savedEntry.getCreatedAt())
                .build();

        try {
            // Elasticsearch에 인덱싱
            elasticsearchService.saveLog(savedEntry);
        } catch (Exception e) {
            log.error("Failed to index log to Elasticsearch: {}", e.getMessage(), e);
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

    public Page<LogEntryResponse> retrieveLogsWith(String keyword, String level, String source, String content,
                                                   LocalDateTime start, LocalDateTime end, Pageable pageable) {
        Page<LogEntry> logEntries = logRepository.searchWithMultipleCriteria(
                keyword, level, source, content, start, end, pageable);

        return logEntries.map(LogEntryResponse::of);
    }
}