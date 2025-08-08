package com.tenacy.logpulse.api;

import com.tenacy.logpulse.api.dto.SystemMetricsResponse;
import com.tenacy.logpulse.service.SystemMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
@Slf4j
public class SystemMetricsController {

    private final SystemMetricsService systemMetricsService;

    @GetMapping("/current")
    public ResponseEntity<SystemMetricsResponse> getCurrentMetrics() {
        SystemMetricsResponse response = SystemMetricsResponse.builder()
                .timestamp(LocalDateTime.now())
                .currentProcessedRate(systemMetricsService.getCurrentProcessedRate())
                .averageProcessedRate(systemMetricsService.getAverageProcessedRate())
                .currentErrorRate(systemMetricsService.getCurrentErrorRate())
                .averageErrorRate(systemMetricsService.getAverageErrorRate())
                .currentResponseTime(systemMetricsService.getCurrentAvgResponseTime())
                .averageResponseTime(systemMetricsService.getAverageResponseTime())
                .build();

        return ResponseEntity.ok(response);
    }
}