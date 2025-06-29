package com.tenacy.logpulse.api;

import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.api.dto.LogEventResponse;
import com.tenacy.logpulse.service.IntegrationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/logs/integration")
@RequiredArgsConstructor
public class LogIntegrationController {

    private final IntegrationLogService integrationLogService;

    @PostMapping
    public ResponseEntity<LogEventResponse> submitLog(@RequestBody LogEventDto logEventDto) {
        if (logEventDto.getTimestamp() == null) {
            logEventDto.setTimestamp(LocalDateTime.now());
        }

        integrationLogService.processLog(logEventDto);

        return ResponseEntity.ok(LogEventResponse.builder()
                .status("success")
                .message("Log event submitted to processing pipeline")
                .timestamp(LocalDateTime.now())
                .build());
    }
}