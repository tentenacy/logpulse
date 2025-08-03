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

@RestController
@RequestMapping("/api/v1/stats/compression")
@RequiredArgsConstructor
@Slf4j
public class CompressionStatsController {

    private final CompressionStatsService compressionStatsService;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getCompressionSummary() {
        Map<String, Object> stats = compressionStatsService.getOverallCompressionStats();

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", java.time.LocalDateTime.now());
        response.put("stats", stats);

        return ResponseEntity.ok(response);
    }

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