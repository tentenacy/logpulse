package com.tenacy.logpulse.api;

import com.tenacy.logpulse.domain.LogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/statistics")
@RequiredArgsConstructor
public class LogStatisticsController {

    private final LogRepository logRepository;

    @GetMapping("/level")
    public ResponseEntity<Map<String, Long>> getLogCountByLevel() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("ERROR", logRepository.countByLogLevel("ERROR"));
        stats.put("WARN", logRepository.countByLogLevel("WARN"));
        stats.put("INFO", logRepository.countByLogLevel("INFO"));
        stats.put("DEBUG", logRepository.countByLogLevel("DEBUG"));
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/source")
    public ResponseEntity<Map<String, Long>> getLogCountBySource() {
        List<Object[]> results = logRepository.countBySourceGrouped();
        Map<String, Long> stats = new HashMap<>();

        for (Object[] result : results) {
            String source = (String) result[0];
            Long count = (Long) result[1];
            stats.put(source, count);
        }

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/hourly")
    public ResponseEntity<List<Map<String, Object>>> getHourlyStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate targetDate = date != null ? date : LocalDate.now();
        List<Object[]> results = logRepository.countByHourAndLevel(targetDate);

        Map<Integer, Map<String, Object>> hourlyMap = new HashMap<>();

        for (Object[] result : results) {
            Integer hour = (Integer) result[0];
            String level = (String) result[1];
            Long count = (Long) result[2];

            hourlyMap.computeIfAbsent(hour, k -> {
                Map<String, Object> hourData = new HashMap<>();
                hourData.put("hour", hour + ":00");
                hourData.put("ERROR", 0L);
                hourData.put("WARN", 0L);
                hourData.put("INFO", 0L);
                hourData.put("DEBUG", 0L);
                hourData.put("total", 0L);
                return hourData;
            });

            Map<String, Object> hourData = hourlyMap.get(hour);
            hourData.put(level, count);
            hourData.put("total", (Long)hourData.get("total") + count);
        }

        List<Map<String, Object>> hourlyStats = new ArrayList<>(hourlyMap.values());
        hourlyStats.sort(Comparator.comparingInt(map -> Integer.parseInt(((String)map.get("hour")).split(":")[0])));

        return ResponseEntity.ok(hourlyStats);
    }
}