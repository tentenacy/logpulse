package com.tenacy.logpulse.api;

import com.tenacy.logpulse.domain.LogRepository;
import com.tenacy.logpulse.service.MetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemStatusController {

    private final LogRepository logRepository;
    private final MetricsService metricsService;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();

        // 처리율 계산 (최근 1분간 로그 수)
        long recentLogsCount = logRepository.countByCreatedAtAfter(
                LocalDateTime.now().minusMinutes(1));

        // 오류율 계산
        long totalCount = logRepository.count();
        long errorCount = logRepository.countByLogLevel("ERROR");
        double errorRate = totalCount > 0 ? ((double) errorCount / totalCount) * 100 : 0;

        // 평균 응답 시간 (가상 데이터 또는 실제 모니터링 데이터)
        int avgResponseTime = metricsService.getAverageResponseTime();

        // 가동 시간 (애플리케이션 시작 시간부터)
        String uptime = metricsService.getFormattedUptime();

        status.put("processedRate", recentLogsCount);
        status.put("errorRate", Math.round(errorRate * 100) / 100.0);
        status.put("avgResponseTime", avgResponseTime);
        status.put("uptime", uptime);

        return ResponseEntity.ok(status);
    }
}