package com.tenacy.logpulse.service;

import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.integration.LogGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
public class IntegrationLogServiceTest {

    @Mock
    private LogGateway logGateway;

    @InjectMocks
    private IntegrationLogService integrationLogService;

    private LogEventDto logEventDto;

    @BeforeEach
    void setUp() {
        logEventDto = LogEventDto.builder()
                .source("test-source")
                .content("Test log content")
                .logLevel("INFO")
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("로그 이벤트 처리 테스트 - 정상 케이스")
    void processLogSuccessTest() {
        // when
        integrationLogService.processLog(logEventDto);

        // then
        verify(logGateway, times(1)).processLog(logEventDto);
    }

    @Test
    @DisplayName("로그 이벤트 처리 테스트 - 타임스탬프 누락 시 자동 설정")
    void processLogWithoutTimestampTest() {
        // given
        LogEventDto eventWithoutTimestamp = LogEventDto.builder()
                .source("test-source")
                .content("Test log content")
                .logLevel("INFO")
                .build();

        // when
        integrationLogService.processLog(eventWithoutTimestamp);

        // then
        verify(logGateway, times(1)).processLog(eventWithoutTimestamp);
        assertNotNull(eventWithoutTimestamp.getTimestamp());
    }

    @Test
    @DisplayName("로그 이벤트 처리 테스트 - 로그 레벨 누락 시 기본값 설정")
    void processLogWithoutLogLevelTest() {
        // given
        LogEventDto eventWithoutLevel = LogEventDto.builder()
                .source("test-source")
                .content("Test log content")
                .timestamp(LocalDateTime.now())
                .build();

        // when
        integrationLogService.processLog(eventWithoutLevel);

        // then
        verify(logGateway, times(1)).processLog(eventWithoutLevel);
        assertEquals("INFO", eventWithoutLevel.getLogLevel());
    }

    @Test
    @DisplayName("로그 이벤트 처리 테스트 - 소스 누락 시 기본값 설정")
    void processLogWithoutSourceTest() {
        // given
        LogEventDto eventWithoutSource = LogEventDto.builder()
                .content("Test log content")
                .logLevel("INFO")
                .timestamp(LocalDateTime.now())
                .build();

        // when
        integrationLogService.processLog(eventWithoutSource);

        // then
        verify(logGateway, times(1)).processLog(eventWithoutSource);
        assertEquals("unknown", eventWithoutSource.getSource());
    }

    @Test
    @DisplayName("로그 이벤트 처리 테스트 - 게이트웨이 예외 발생 시 처리")
    void processLogExceptionTest() {
        // given
        doThrow(new RuntimeException("Test exception")).when(logGateway).processLog(any(LogEventDto.class));

        // when
        integrationLogService.processLog(logEventDto);

        // then
        // 예외가 발생해도 메서드가 실행을 완료해야 함
        verify(logGateway, times(1)).processLog(logEventDto);
    }

    // Helper methods
    private void assertNotNull(LocalDateTime timestamp) {
        if (timestamp == null) {
            throw new AssertionError("timestamp should not be null");
        }
    }

    private void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected '" + expected + "' but got '" + actual + "'");
        }
    }
}