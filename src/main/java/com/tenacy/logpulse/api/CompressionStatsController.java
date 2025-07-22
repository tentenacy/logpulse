package com.tenacy.logpulse.api;

import com.tenacy.logpulse.service.CompressionStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 로그 압축 통계 API 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/stats/compression")
@RequiredArgsConstructor
@Slf4j
public class CompressionStatsController {

    private final CompressionStatsService compressionStatsService;

    /**
     * 전체 압축 통계 요약 조회
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getCompressionSummary() {
        Map<String, Object> stats = compressionStatsService.getOverallCompressionStats();

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", java.time.LocalDateTime.now());
        response.put("stats", stats);

        return ResponseEntity.ok(response);
    }

    /**
     * 특정 날짜의 압축 통계 조회
     */
    @GetMapping("/daily")
    public ResponseEntity<Map<String, Object>> getDailyCompressionStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate targetDate = date != null ? date : LocalDate.now().minusDays(1);
        Map<String, Object> stats = compressionStatsService.getCompressionStatsForDate(targetDate);

        Map<String, Object> response = new HashMap<>();
        response.put("date", targetDate);
        response.put("timestamp", java.time.LocalDateTime.now());
        response.put("stats", stats);

        return ResponseEntity.ok(response);
    }

    /**
     * 날짜 범위의 압축 통계 조회
     */
    @GetMapping("/range")
    public ResponseEntity<Map<String, Object>> getCompressionStatsForRange(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        LocalDate endDate = end != null ? end : LocalDate.now();
        LocalDate startDate = start != null ? start : endDate.minusDays(7);

        List<Map<String, Object>> stats = compressionStatsService.getCompressionStatsForDateRange(startDate, endDate);

        Map<String, Object> response = new HashMap<>();
        response.put("startDate", startDate);
        response.put("endDate", endDate);
        response.put("timestamp", java.time.LocalDateTime.now());
        response.put("stats", stats);

        return ResponseEntity.ok(response);
    }
}