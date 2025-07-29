package com.tenacy.logpulse.service;

import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.integration.LogGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationLogService {

    private final LogGateway logGateway;

    public void processLog(LogEventDto logEventDto) {
        try {
            // 기본값 설정
            if (logEventDto.getTimestamp() == null) {
                logEventDto.setTimestamp(LocalDateTime.now());
            }

            // 로그 레벨 확인 및 기본값 설정
            if (logEventDto.getLogLevel() == null || logEventDto.getLogLevel().trim().isEmpty()) {
                logEventDto.setLogLevel("INFO");
            }

            // 소스 확인 및 기본값 설정
            if (logEventDto.getSource() == null || logEventDto.getSource().trim().isEmpty()) {
                logEventDto.setSource("unknown");
            }

            log.debug("로그 이벤트를 통합 파이프라인으로 전송: {}", logEventDto);

            // 로그 게이트웨이를 통해 통합 흐름으로 전송
            logGateway.processLog(logEventDto);

        } catch (Exception e) {
            log.error("로그 이벤트 처리 중 오류 발생: {}", e.getMessage(), e);

            // 성능 테스트용 로그는 한번 재시도
            if ("performance-test".equals(logEventDto.getSource())) {
                try {
                    Thread.sleep(100);
                    log.debug("로그 이벤트 재시도: {}", logEventDto);
                    logGateway.processLog(logEventDto);
                } catch (Exception retryEx) {
                    log.error("재시도 실패: {}", retryEx.getMessage());
                }
            }
        }
    }
}