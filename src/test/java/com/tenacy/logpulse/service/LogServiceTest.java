package com.tenacy.logpulse.service;

import com.tenacy.logpulse.api.dto.LogEntryRequest;
import com.tenacy.logpulse.api.dto.LogEntryResponse;
import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.domain.LogRepository;
import com.tenacy.logpulse.elasticsearch.service.ElasticsearchService;
import com.tenacy.logpulse.pattern.LogPatternDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
public class LogServiceTest {

    @Mock
    private LogRepository logRepository;

    @Mock
    private ElasticsearchService elasticsearchService;

    @Mock
    private LogMetricsService logMetricsService;

    @Mock
    private RealTimeErrorMonitorService errorMonitorService;

    @Mock
    private LogPatternDetector patternDetector;

    @Mock
    private LogCompressionService compressionService;

    @Mock
    private LogStatisticsService logStatisticsService;

    @InjectMocks
    private LogService logService;

    private LogEntryRequest logEntryRequest;
    private LogEntry savedLogEntry;

    @BeforeEach
    void setUp() {
        // 테스트에 사용할 로그 요청 객체 생성
        logEntryRequest = LogEntryRequest.builder()
                .source("test-service")
                .content("테스트 로그 메시지")
                .logLevel("INFO")
                .build();

        // 저장된 로그 엔트리 객체 생성
        savedLogEntry = LogEntry.builder()
                .id(1L)
                .source("test-service")
                .content("테스트 로그 메시지")
                .logLevel("INFO")
                .createdAt(LocalDateTime.now())
                .compressed(false)
                .originalSize(logEntryRequest.getContent().getBytes().length)
                .compressedSize(logEntryRequest.getContent().getBytes().length)
                .build();
    }

    @Test
    @DisplayName("로그 생성 - 정상 케이스")
    void createLog_ShouldSaveAndReturnLogEntry() {
        // given
        when(compressionService.shouldCompress(anyString())).thenReturn(false);
        when(logRepository.save(any(LogEntry.class))).thenReturn(savedLogEntry);

        // when
        LogEntryResponse response = logService.createLog(logEntryRequest);

        // then
        assertNotNull(response);
        assertEquals(savedLogEntry.getId(), response.getId());
        assertEquals(savedLogEntry.getSource(), response.getSource());
        assertEquals(savedLogEntry.getContent(), response.getContent());
        assertEquals(savedLogEntry.getLogLevel(), response.getLogLevel());

        // 로그 저장 검증
        ArgumentCaptor<LogEntry> logCaptor = ArgumentCaptor.forClass(LogEntry.class);
        verify(logRepository).save(logCaptor.capture());
        assertEquals(logEntryRequest.getContent(), logCaptor.getValue().getContent());

        // 통계 업데이트 검증
        verify(logStatisticsService).updateStatistics(
                eq(logEntryRequest.getSource()),
                eq(logEntryRequest.getLogLevel()),
                any(LocalDateTime.class));
    }

    @Test
    @DisplayName("로그 조회 - 필터링 테스트")
    void retrieveLogs_ShouldFilterByParameters() {
        // given
        String keyword = "에러";
        String level = "ERROR";
        String source = "api-server";
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now();
        Pageable pageable = PageRequest.of(0, 10);

        List<LogEntry> logEntries = Arrays.asList(
                LogEntry.builder()
                        .id(1L)
                        .source("api-server")
                        .content("에러 발생")
                        .logLevel("ERROR")
                        .createdAt(LocalDateTime.now().minusMinutes(30))
                        .compressed(false)
                        .build()
        );

        Page<LogEntry> logPage = new PageImpl<>(logEntries, pageable, 1);

        when(logRepository.searchWithMultipleCriteria(
                eq(keyword), eq(level), eq(source), eq(null), eq(start), eq(end), eq(pageable)))
                .thenReturn(logPage);

        // when
        Page<LogEntryResponse> result = logService.retrieveLogsWith(
                keyword, level, source, null, start, end, pageable);

        // then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(logEntries.get(0).getSource(), result.getContent().get(0).getSource());
        assertEquals(logEntries.get(0).getLogLevel(), result.getContent().get(0).getLogLevel());
        assertEquals(logEntries.get(0).getContent(), result.getContent().get(0).getContent());

        // 검색 파라미터 검증
        verify(logRepository).searchWithMultipleCriteria(
                eq(keyword), eq(level), eq(source), eq(null), eq(start), eq(end), eq(pageable));
    }

    @Test
    @DisplayName("로그 압축 - 로그 압축 및 압축 해제 테스트")
    void createLog_ShouldCompressContent() {
        // given
        String originalContent = "이것은 압축될 긴 로그 메시지입니다.".repeat(50); // 길게 반복
        String compressedContent = "압축된내용";

        LogEntryRequest compressibleRequest = LogEntryRequest.builder()
                .source("test-service")
                .content(originalContent)
                .logLevel("INFO")
                .build();

        LogEntry compressedEntry = LogEntry.builder()
                .id(1L)
                .source("test-service")
                .content(compressedContent)
                .logLevel("INFO")
                .createdAt(LocalDateTime.now())
                .compressed(true)
                .originalSize(originalContent.getBytes().length)
                .compressedSize(compressedContent.getBytes().length)
                .build();

        when(compressionService.shouldCompress(originalContent)).thenReturn(true);
        when(compressionService.compressContent(originalContent)).thenReturn(compressedContent);
        when(logRepository.save(any(LogEntry.class))).thenReturn(compressedEntry);

        // when
        LogEntryResponse response = logService.createLog(compressibleRequest);

        // then
        assertNotNull(response);
        assertEquals(originalContent, response.getContent()); // 응답은 원본 내용을 포함해야 함

        // 압축 로직 검증
        verify(compressionService).shouldCompress(originalContent);
        verify(compressionService).compressContent(originalContent);

        // 저장 시 압축된 내용 검증
        ArgumentCaptor<LogEntry> logCaptor = ArgumentCaptor.forClass(LogEntry.class);
        verify(logRepository).save(logCaptor.capture());
        LogEntry savedLog = logCaptor.getValue();
        assertEquals(true, savedLog.getCompressed());
        assertEquals(compressedContent, savedLog.getContent());
    }
}