package com.tenacy.logpulse.api;

import com.tenacy.logpulse.api.dto.DashboardStatsResponse;
import com.tenacy.logpulse.api.dto.LogCountResponse;
import com.tenacy.logpulse.api.dto.SystemStatusResponse;
import com.tenacy.logpulse.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsResponse> getDashboardStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) String source) {

        DashboardStatsResponse stats = dashboardService.getDashboardStats(start, end, source);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/log-counts")
    public ResponseEntity<LogCountResponse> getLogCounts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) String source) {

        LogCountResponse counts = dashboardService.getLogCounts(start, end, source);
        return ResponseEntity.ok(counts);
    }

    @GetMapping("/hourly-stats")
    public ResponseEntity<Map<String, Object>> getHourlyStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate targetDate = date != null ? date : LocalDate.now();
        Map<String, Object> hourlyStats = dashboardService.getHourlyStats(targetDate);
        return ResponseEntity.ok(hourlyStats);
    }

    @GetMapping("/source-stats")
    public ResponseEntity<Map<String, Object>> getSourceStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        Map<String, Object> sourceStats = dashboardService.getSourceStats(start, end);
        return ResponseEntity.ok(sourceStats);
    }

    @GetMapping("/system-status")
    public ResponseEntity<SystemStatusResponse> getSystemStatus() {
        SystemStatusResponse systemStatus = dashboardService.getSystemStatus();
        return ResponseEntity.ok(systemStatus);
    }

    @GetMapping("/recent-errors")
    public ResponseEntity<Map<String, Object>> getRecentErrors() {
        Map<String, Object> recentErrors = dashboardService.getRecentErrors();
        return ResponseEntity.ok(recentErrors);
    }
}