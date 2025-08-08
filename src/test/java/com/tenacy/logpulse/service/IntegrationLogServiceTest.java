package com.tenacy.logpulse.service;

import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.integration.LogGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
public class IntegrationLogServiceTest {

    @Mock
    private LogGateway logGateway;

    @InjectMocks
    private IntegrationLogService integrationLogService;

    @Test
    @DisplayName("로그 이벤트 처리 - 정상 케이스")
    void processLog_ShouldHandleLogEventCorrectly() {
        // given
        LogEventDto logEventDto = LogEventDto.builder()
                .source("test-source")
                .content("테스트 로그 내용")
                .logLevel("INFO")
                .timestamp(LocalDateTime.now())
                .build();

        // when
        integrationLogService.processLog(logEventDto);

        // then
        ArgumentCaptor<LogEventDto> captor = ArgumentCaptor.forClass(LogEventDto.class);
        verify(logGateway).processLog(captor.capture());

        LogEventDto processedDto = captor.getValue();
        assertEquals(logEventDto.getSource(), processedDto.getSource());
        assertEquals(logEventDto.getContent(), processedDto.getContent());
        assertEquals(logEventDto.getLogLevel(), processedDto.getLogLevel());
    }

    @Test
    @DisplayName("타임스탬프 누락 시 자동 설정")
    void processLog_ShouldSetTimestampIfMissing() {
        // given
        LogEventDto logEventDto = LogEventDto.builder()
                .source("test-source")
                .content("테스트 로그 내용")
                .logLevel("INFO")
                // 타임스탬프 의도적 누락
                .build();

        // when
        integrationLogService.processLog(logEventDto);

        // then
        ArgumentCaptor<LogEventDto> captor = ArgumentCaptor.forClass(LogEventDto.class);
        verify(logGateway).processLog(captor.capture());

        LogEventDto processedDto = captor.getValue();
        assertNotNull(processedDto.getTimestamp(), "타임스탬프가 자동으로 설정되어야 함");
    }

    @Test
    @DisplayName("로그 레벨 누락 시 기본값 설정")
    void processLog_ShouldSetDefaultLogLevelIfMissing() {
        // given
        LogEventDto logEventDto = LogEventDto.builder()
                .source("test-source")
                .content("테스트 로그 내용")
                // 로그 레벨 의도적 누락
                .timestamp(LocalDateTime.now())
                .build();

        // when
        integrationLogService.processLog(logEventDto);

        // then
        ArgumentCaptor<LogEventDto> captor = ArgumentCaptor.forClass(LogEventDto.class);
        verify(logGateway).processLog(captor.capture());

        LogEventDto processedDto = captor.getValue();
        assertEquals("INFO", processedDto.getLogLevel(), "기본 로그 레벨이 INFO로 설정되어야 함");
    }

    @Test
    @DisplayName("소스 누락 시 기본값 설정")
    void processLog_ShouldSetDefaultSourceIfMissing() {
        // given
        LogEventDto logEventDto = LogEventDto.builder()
                // 소스 의도적 누락
                .content("테스트 로그 내용")
                .logLevel("INFO")
                .timestamp(LocalDateTime.now())
                .build();

        // when
        integrationLogService.processLog(logEventDto);

        // then
        ArgumentCaptor<LogEventDto> captor = ArgumentCaptor.forClass(LogEventDto.class);
        verify(logGateway).processLog(captor.capture());

        LogEventDto processedDto = captor.getValue();
        assertEquals("unknown", processedDto.getSource(), "기본 소스가 'unknown'으로 설정되어야 함");
    }

    @Test
    @DisplayName("로그 게이트웨이 예외 발생 시 처리")
    void processLog_ShouldHandleExceptions() {
        // given
        LogEventDto logEventDto = LogEventDto.builder()
                .source("test-source")
                .content("테스트 로그 내용")
                .logLevel("INFO")
                .timestamp(LocalDateTime.now())
                .build();

        doThrow(new RuntimeException("테스트 예외")).when(logGateway).processLog(any(LogEventDto.class));

        // when & then
        // 예외가 발생해도 서비스 메서드가 예외를 전파하지 않고 정상 완료해야 함
        assertDoesNotThrow(() -> integrationLogService.processLog(logEventDto));

        // 예외가 발생해도 로그 게이트웨이를 호출했는지 확인
        verify(logGateway).processLog(any(LogEventDto.class));
    }
}