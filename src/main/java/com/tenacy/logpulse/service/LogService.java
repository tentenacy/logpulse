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

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

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
    private final LogCompressionService compressionService;

    @Transactional
    public LogEntryResponse createLog(LogEntryRequest request) {
        String originalContent = request.getContent();

        // 원본 크기 계산
        int originalSize = originalContent != null ?
                originalContent.getBytes(StandardCharsets.UTF_8).length : 0;

        // 압축 적용 여부 결정 및 압축 처리
        boolean shouldCompress = compressionService.shouldCompress(originalContent);
        String finalContent = originalContent;
        int compressedSize = originalSize;

        if (shouldCompress) {
            finalContent = compressionService.compressContent(originalContent);
            compressedSize = finalContent.getBytes(StandardCharsets.UTF_8).length;
            log.debug("로그 내용 압축됨: {}% 감소",
                    Math.round((1 - (double)compressedSize/originalSize) * 100));
        }

        LogEntry logEntry = LogEntry.builder()
                .source(request.getSource())
                .content(finalContent)
                .logLevel(request.getLogLevel())
                .createdAt(LocalDateTime.now())
                .compressed(shouldCompress)
                .originalSize(originalSize)
                .compressedSize(compressedSize)
                .build();

        LogEntry savedEntry = logRepository.save(logEntry);
        log.debug("로그 항목이 데이터베이스에 저장됨: {}", savedEntry.getId());

        LogEventDto eventDto = LogEventDto.builder()
                .source(savedEntry.getSource())
                .content(originalContent)
                .logLevel(savedEntry.getLogLevel())
                .timestamp(savedEntry.getCreatedAt())
                .build();

        try {
            // Elasticsearch에 인덱싱
            elasticsearchService.saveLog(savedEntry, originalContent);
        } catch (Exception e) {
            log.error("로그를 Elasticsearch에 인덱싱하는 중 오류 발생: {}", e.getMessage(), e);
        }

        try {
            // 메트릭 수집
            logMetricsService.recordLog(eventDto);
        } catch (Exception e) {
            log.error("메트릭을 기록하는 중 오류 발생: {}", e.getMessage(), e);
        }

        try {
            // 실시간 오류 모니터링
            if ("ERROR".equalsIgnoreCase(savedEntry.getLogLevel())) {
                errorMonitorService.monitorLog(eventDto);
            }
        } catch (Exception e) {
            log.error("오류를 모니터링하는 중 오류 발생: {}", e.getMessage(), e);
        }

        // 패턴 감지 수행
        LogEntry uncompressedEntry = LogEntry.builder()
                .id(savedEntry.getId())
                .source(savedEntry.getSource())
                .content(originalContent)
                .logLevel(savedEntry.getLogLevel())
                .createdAt(savedEntry.getCreatedAt())
                .compressed(false)
                .originalSize(savedEntry.getOriginalSize())
                .compressedSize(savedEntry.getCompressedSize())
                .build();

        patternDetector.processLog(uncompressedEntry);

        // 응답 생성
        return LogEntryResponse.builder()
                .id(savedEntry.getId())
                .source(savedEntry.getSource())
                .content(originalContent)
                .logLevel(savedEntry.getLogLevel())
                .createdAt(savedEntry.getCreatedAt())
                .build();
    }

    public Page<LogEntryResponse> retrieveLogsWith(
            String keyword, String level, String source, String content,
            LocalDateTime start, LocalDateTime end, Pageable pageable) {

        Page<LogEntry> logEntries = logRepository.searchWithMultipleCriteria(
                keyword, level, source, content, start, end, pageable);

        return logEntries.map(this::createLogEntryResponseWithDecompression);
    }

    private LogEntryResponse createLogEntryResponseWithDecompression(LogEntry logEntry) {
        String contentToUse = logEntry.getContent();

        // 압축된 경우 압축 해제
        if (Boolean.TRUE.equals(logEntry.getCompressed()) && contentToUse != null) {
            contentToUse = compressionService.decompressContent(contentToUse);
        }

        return LogEntryResponse.builder()
                .id(logEntry.getId())
                .source(logEntry.getSource())
                .content(contentToUse)
                .logLevel(logEntry.getLogLevel())
                .createdAt(logEntry.getCreatedAt())
                .build();
    }
}