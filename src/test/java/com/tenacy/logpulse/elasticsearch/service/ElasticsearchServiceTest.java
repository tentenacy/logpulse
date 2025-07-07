package com.tenacy.logpulse.elasticsearch.service;

import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.elasticsearch.document.LogDocument;
import com.tenacy.logpulse.elasticsearch.repository.LogDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
public class ElasticsearchServiceTest {

    @Mock
    private LogDocumentRepository logDocumentRepository;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @InjectMocks
    private ElasticsearchService elasticsearchService;

    @Captor
    private ArgumentCaptor<LogDocument> logDocumentCaptor;

    @Captor
    private ArgumentCaptor<List<LogDocument>> logDocumentsCaptor;

    private LogEntry sampleLogEntry;
    private LogDocument sampleLogDocument;

    @BeforeEach
    void setUp() {
        sampleLogEntry = LogEntry.builder()
                .id(1L)
                .source("test-service")
                .content("Test log message")
                .logLevel("INFO")
                .createdAt(LocalDateTime.now())
                .build();

        sampleLogDocument = LogDocument.of(sampleLogEntry);
    }

    @Test
    @DisplayName("단일 로그 저장 테스트")
    void saveLogTest() {
        // when
        elasticsearchService.saveLog(sampleLogEntry);

        // then
        verify(logDocumentRepository, times(1)).save(logDocumentCaptor.capture());
        LogDocument capturedDocument = logDocumentCaptor.getValue();
        assertThat(capturedDocument.getSource()).isEqualTo(sampleLogEntry.getSource());
        assertThat(capturedDocument.getContent()).isEqualTo(sampleLogEntry.getContent());
        assertThat(capturedDocument.getLogLevel()).isEqualTo(sampleLogEntry.getLogLevel());
        assertThat(capturedDocument.getTimestamp()).isEqualTo(sampleLogEntry.getCreatedAt());
    }

    @Test
    @DisplayName("여러 로그 저장 테스트")
    void saveAllTest() {
        // given
        LogEntry anotherLogEntry = LogEntry.builder()
                .id(2L)
                .source("another-service")
                .content("Another log message")
                .logLevel("ERROR")
                .createdAt(LocalDateTime.now())
                .build();
        List<LogEntry> logEntries = Arrays.asList(sampleLogEntry, anotherLogEntry);

        // when
        elasticsearchService.saveAll(logEntries);

        // then
        verify(logDocumentRepository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("로그 레벨별 검색 테스트")
    void findByLogLevelTest() {
        // given
        String logLevel = "ERROR";
        List<LogDocument> errorLogs = Collections.singletonList(LogDocument.builder()
                .id("2")
                .source("error-service")
                .content("Error log message")
                .logLevel(logLevel)
                .timestamp(LocalDateTime.now())
                .build());
        when(logDocumentRepository.findByLogLevel(logLevel)).thenReturn(errorLogs);

        // when
        List<LogDocument> result = elasticsearchService.findByLogLevel(logLevel);

        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLogLevel()).isEqualTo(logLevel);
        verify(logDocumentRepository, times(1)).findByLogLevel(logLevel);
    }

    @Test
    @DisplayName("소스 내용으로 검색 테스트")
    void findBySourceContainingTest() {
        // given
        String sourceContains = "test";
        List<LogDocument> sourceLogs = Collections.singletonList(sampleLogDocument);
        when(logDocumentRepository.findBySourceContaining(sourceContains)).thenReturn(sourceLogs);

        // when
        List<LogDocument> result = elasticsearchService.findBySourceContaining(sourceContains);

        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSource()).contains(sourceContains);
        verify(logDocumentRepository, times(1)).findBySourceContaining(sourceContains);
    }

    @Test
    @DisplayName("키워드 검색 테스트")
    void searchByKeywordTest() {
        // given
        String keyword = "test";
        SearchHit<LogDocument> searchHit = mock(SearchHit.class);
        when(searchHit.getContent()).thenReturn(sampleLogDocument);

        SearchHits<LogDocument> searchHits = mock(SearchHits.class);
        when(searchHits.getSearchHits()).thenReturn(Collections.singletonList(searchHit));

        when(elasticsearchOperations.search(any(NativeQuery.class), eq(LogDocument.class))).thenReturn(searchHits);

        // when
        List<LogDocument> result = elasticsearchService.searchByKeyword(keyword);

        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(sampleLogDocument);
        verify(elasticsearchOperations, times(1)).search(any(NativeQuery.class), eq(LogDocument.class));
    }

    @Test
    @DisplayName("기간별 검색 테스트")
    void findByTimestampBetweenTest() {
        // given
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now();
        List<LogDocument> periodLogs = Collections.singletonList(sampleLogDocument);
        when(logDocumentRepository.findByTimestampBetween(start, end)).thenReturn(periodLogs);

        // when
        List<LogDocument> result = elasticsearchService.findByTimestampBetween(start, end);

        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(sampleLogDocument);
        verify(logDocumentRepository, times(1)).findByTimestampBetween(start, end);
    }
}