package com.tenacy.logpulse.service;

import com.tenacy.logpulse.api.dto.LogEntryRequest;
import com.tenacy.logpulse.api.dto.LogEntryResponse;
import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.domain.LogRepository;
import com.tenacy.logpulse.elasticsearch.service.ElasticsearchService;
import com.tenacy.logpulse.pattern.LogPatternDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
public class LogServiceTest {

    @Mock
    private LogRepository logRepository;

    @Mock
    private ElasticsearchService elasticsearchService;

    @Mock
    private LogMetricsService logMetricsService;

    @Mock
    private LogPatternDetector patternDetector;

    @InjectMocks
    private LogService logService;

    private LogEntry sampleLogEntry;
    private LogEntryRequest sampleRequest;

    @BeforeEach
    void setUp() {
        // 테스트용 샘플 데이터 설정
        sampleLogEntry = LogEntry.builder()
                .id(1L)
                .source("test-service")
                .content("This is a test log message")
                .logLevel("INFO")
                .createdAt(LocalDateTime.now())
                .build();

        sampleRequest = LogEntryRequest.builder()
                .source("test-service")
                .content("This is a test log message")
                .logLevel("INFO")
                .build();
    }

    @Test
    @DisplayName("로그 생성 테스트")
    void createLogTest() {
        // given
        when(logRepository.save(any(LogEntry.class))).thenReturn(sampleLogEntry);

        // when
        LogEntryResponse response = logService.createLog(sampleRequest);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(sampleLogEntry.getId());
        assertThat(response.getSource()).isEqualTo(sampleLogEntry.getSource());
        assertThat(response.getContent()).isEqualTo(sampleLogEntry.getContent());
        assertThat(response.getLogLevel()).isEqualTo(sampleLogEntry.getLogLevel());

        verify(logRepository, times(1)).save(any(LogEntry.class));
        verify(elasticsearchService, times(1)).saveLog(any(LogEntry.class));
        verify(logMetricsService, times(1)).recordLog(any(LogEventDto.class));
        verify(patternDetector, times(1)).detectPatterns(any(LogEntry.class));
    }

    @Test
    @DisplayName("모든 로그 조회 테스트")
    void retrieveAllLogsTest() {
        // given
        List<LogEntry> logEntries = Arrays.asList(
                sampleLogEntry,
                LogEntry.builder()
                        .id(2L)
                        .source("another-service")
                        .content("Another test log message")
                        .logLevel("ERROR")
                        .createdAt(LocalDateTime.now())
                        .build()
        );
        when(logRepository.findAll()).thenReturn(logEntries);

        // when
        List<LogEntryResponse> responses = logService.retrieveAllLogs();

        // then
        assertThat(responses).isNotNull();
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getId()).isEqualTo(logEntries.get(0).getId());
        assertThat(responses.get(1).getId()).isEqualTo(logEntries.get(1).getId());
        verify(logRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("로그 레벨별 조회 테스트")
    void retrieveLogsByLevelTest() {
        // given
        String logLevel = "ERROR";
        List<LogEntry> errorLogs = List.of(
                LogEntry.builder()
                        .id(2L)
                        .source("error-service")
                        .content("Error log message")
                        .logLevel(logLevel)
                        .createdAt(LocalDateTime.now())
                        .build()
        );
        when(logRepository.findByLogLevel(logLevel)).thenReturn(errorLogs);

        // when
        List<LogEntryResponse> responses = logService.retrieveLogsByLevel(logLevel);

        // then
        assertThat(responses).isNotNull();
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getLogLevel()).isEqualTo(logLevel);
        verify(logRepository, times(1)).findByLogLevel(logLevel);
    }

    @Test
    @DisplayName("기간별 로그 조회 테스트")
    void retrieveLogsBetweenTest() {
        // given
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now();
        List<LogEntry> periodLogs = List.of(sampleLogEntry);
        when(logRepository.findByCreatedAtBetween(start, end)).thenReturn(periodLogs);

        // when
        List<LogEntryResponse> responses = logService.retrieveLogsBetween(start, end);

        // then
        assertThat(responses).isNotNull();
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getId()).isEqualTo(sampleLogEntry.getId());
        verify(logRepository, times(1)).findByCreatedAtBetween(start, end);
    }
}