package com.tenacy.logpulse.service;

import com.tenacy.logpulse.api.dto.DashboardStatsResponse;
import com.tenacy.logpulse.api.dto.LogCountResponse;
import com.tenacy.logpulse.api.dto.LogEntryResponse;
import com.tenacy.logpulse.api.dto.SystemStatusResponse;
import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.domain.LogRepository;
import com.tenacy.logpulse.domain.LogStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final LogRepository logRepository;
    private final LogStatisticsRepository logStatisticsRepository;
    private final SystemMetricsService systemMetricsService;

    public DashboardStatsResponse getDashboardStats(LocalDateTime start, LocalDateTime end, String source) {
        // 기본 시간 범위 설정
        LocalDateTime endTime = end != null ? end : LocalDateTime.now();
        LocalDateTime startTime = start != null ? start : endTime.minusHours(24);

        // 로그 레벨별 카운트 (통계 테이블 사용)
        LogCountResponse logCounts = getLogCountsFromStats(startTime, endTime, source);

        // 시간별 통계 (통계 테이블 사용)
        Map<String, Object> hourlyStats = getHourlyStatsFromStats(LocalDate.now());

        // 소스별 통계 (통계 테이블 사용)
        Map<String, Object> sourceStats = getSourceStatsFromStats(startTime, endTime);

        // 시스템 상태
        SystemStatusResponse systemStatus = getSystemStatus();

        // 최근 오류 로그 (원본 로그 테이블 사용)
        Map<String, Object> recentErrors = getRecentErrors();

        // 오류 추세 데이터
        Map<String, Object> errorTrends = getErrorTrends(startTime.minusDays(7), endTime);

        return DashboardStatsResponse.builder()
                .logCounts(logCounts)
                .hourlyStats(hourlyStats)
                .sourceStats(sourceStats)
                .systemStatus(systemStatus)
                .recentErrors(recentErrors)
                .errorTrends(errorTrends)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public LogCountResponse getLogCountsFromStats(LocalDateTime start, LocalDateTime end, String source) {
        try {
            // 날짜 범위 계산
            LocalDate startDate = start.toLocalDate();
            LocalDate endDate = end.toLocalDate();

            // 로그 레벨별 집계 쿼리
            List<Object[]> levelStats;

            if (source != null && !source.isEmpty()) {
                // 소스 필터가 있는 경우의 쿼리 (커스텀 쿼리 필요)
                levelStats = logStatisticsRepository.findLevelStatsByDateRangeAndSource(startDate, endDate, source);
            } else {
                // 전체 통계
                levelStats = logStatisticsRepository.findLevelStatsByDateRange(startDate, endDate);
            }

            // 결과 파싱
            long errorCount = 0L;
            long warnCount = 0L;
            long infoCount = 0L;
            long debugCount = 0L;
            long totalCount = 0L;

            for (Object[] row : levelStats) {
                String level = (String) row[0];
                Long count = ((Number) row[1]).longValue();

                switch (level.toUpperCase()) {
                    case "ERROR":
                        errorCount = count;
                        break;
                    case "WARN":
                        warnCount = count;
                        break;
                    case "INFO":
                        infoCount = count;
                        break;
                    case "DEBUG":
                        debugCount = count;
                        break;
                }

                totalCount += count;
            }

            // 오류율 계산
            double errorRate = totalCount > 0 ? (double) errorCount / totalCount * 100 : 0;

            return LogCountResponse.builder()
                    .error(errorCount)
                    .warn(warnCount)
                    .info(infoCount)
                    .debug(debugCount)
                    .total(totalCount)
                    .errorRate(Math.round(errorRate * 100.0) / 100.0)
                    .build();
        } catch (Exception e) {
            log.error("통계 테이블에서 로그 카운트 조회 중 오류 발생", e);

            // 오류 발생 시 원본 로그 테이블에서 조회 (기존 메서드 호출)
            return getLogCounts(start, end, source);
        }
    }

    public LogCountResponse getLogCounts(LocalDateTime start, LocalDateTime end, String source) {
        try {
            // 기본 시간 범위 설정 (기본: 최근 24시간)
            LocalDateTime endTime = end != null ? end : LocalDateTime.now();
            LocalDateTime startTime = start != null ? start : endTime.minusHours(24);

            long errorCount;
            long warnCount;
            long infoCount;
            long debugCount;
            long totalCount;

            if (source != null && !source.isEmpty()) {
                // 소스 필터가 있는 경우
                errorCount = logRepository.countByLogLevelAndSourceContainingAndCreatedAtBetween("ERROR", source, startTime, endTime);
                warnCount = logRepository.countByLogLevelAndSourceContainingAndCreatedAtBetween("WARN", source, startTime, endTime);
                infoCount = logRepository.countByLogLevelAndSourceContainingAndCreatedAtBetween("INFO", source, startTime, endTime);
                debugCount = logRepository.countByLogLevelAndSourceContainingAndCreatedAtBetween("DEBUG", source, startTime, endTime);
                totalCount = logRepository.countBySourceContainingAndCreatedAtBetween(source, startTime, endTime);
            } else {
                // 소스 필터가 없는 경우
                errorCount = logRepository.countByLogLevelAndCreatedAtBetween("ERROR", startTime, endTime);
                warnCount = logRepository.countByLogLevelAndCreatedAtBetween("WARN", startTime, endTime);
                infoCount = logRepository.countByLogLevelAndCreatedAtBetween("INFO", startTime, endTime);
                debugCount = logRepository.countByLogLevelAndCreatedAtBetween("DEBUG", startTime, endTime);
                totalCount = logRepository.countByCreatedAtBetween(startTime, endTime);
            }

            // 오류율 계산
            double errorRate = totalCount > 0 ? (double) errorCount / totalCount * 100 : 0;

            return LogCountResponse.builder()
                    .error(errorCount)
                    .warn(warnCount)
                    .info(infoCount)
                    .debug(debugCount)
                    .total(totalCount)
                    .errorRate(Math.round(errorRate * 100.0) / 100.0)
                    .build();
        } catch (Exception e) {
            log.error("로그 카운트 조회 중 오류 발생", e);
            return LogCountResponse.builder()
                    .error(0L)
                    .warn(0L)
                    .info(0L)
                    .debug(0L)
                    .total(0L)
                    .errorRate(0.0)
                    .build();
        }
    }

    public Map<String, Object> getHourlyStats(LocalDate date) {
        try {
            List<Map<String, Object>> hourlyStats = new ArrayList<>();
            LocalDateTime startOfDay = date.atStartOfDay();

            // 24시간에 대한 데이터 생성
            for (int hour = 0; hour < 24; hour++) {
                LocalDateTime hourStart = startOfDay.plusHours(hour);
                LocalDateTime hourEnd = hourStart.plusHours(1);

                long errorCount = logRepository.countByLogLevelAndCreatedAtBetween("ERROR", hourStart, hourEnd);
                long warnCount = logRepository.countByLogLevelAndCreatedAtBetween("WARN", hourStart, hourEnd);
                long infoCount = logRepository.countByLogLevelAndCreatedAtBetween("INFO", hourStart, hourEnd);
                long debugCount = logRepository.countByLogLevelAndCreatedAtBetween("DEBUG", hourStart, hourEnd);
                long totalCount = errorCount + warnCount + infoCount + debugCount;

                Map<String, Object> hourData = new HashMap<>();
                hourData.put("hour", String.format("%02d:00", hour));
                hourData.put("error", errorCount);
                hourData.put("warn", warnCount);
                hourData.put("info", infoCount);
                hourData.put("debug", debugCount);
                hourData.put("total", totalCount);

                hourlyStats.add(hourData);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("date", date);
            result.put("hourlyStats", hourlyStats);
            return result;
        } catch (Exception e) {
            log.error("시간별 통계 조회 중 오류 발생", e);
            return Map.of("date", date, "hourlyStats", List.of());
        }
    }

    public Map<String, Object> getHourlyStatsFromStats(LocalDate date) {
        try {
            // 통계 테이블에서 시간별 데이터 조회
            List<Object[]> hourlyData = logStatisticsRepository.findHourlyStatsByDate(date);

            // 결과를 시간별로 구성
            Map<Integer, Map<String, Long>> hourMap = new HashMap<>();

            for (Object[] row : hourlyData) {
                Integer hour = (Integer) row[0];
                String level = (String) row[1];
                Long count = ((Number) row[2]).longValue();

                hourMap.computeIfAbsent(hour, k -> new HashMap<>())
                        .put(level, count);
            }

            // 응답 포맷에 맞게 변환
            List<Map<String, Object>> hourlyStats = new ArrayList<>();

            for (int hour = 0; hour < 24; hour++) {
                Map<String, Long> counts = hourMap.getOrDefault(hour, Map.of());

                Map<String, Object> hourData = new HashMap<>();
                hourData.put("hour", String.format("%02d:00", hour));
                hourData.put("error", counts.getOrDefault("ERROR", 0L));
                hourData.put("warn", counts.getOrDefault("WARN", 0L));
                hourData.put("info", counts.getOrDefault("INFO", 0L));
                hourData.put("debug", counts.getOrDefault("DEBUG", 0L));

                long total = counts.values().stream().mapToLong(Long::longValue).sum();
                hourData.put("total", total);

                hourlyStats.add(hourData);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("date", date);
            result.put("hourlyStats", hourlyStats);
            return result;
        } catch (Exception e) {
            log.error("통계 테이블에서 시간별 통계 조회 중 오류 발생", e);

            // 오류 발생 시 원본 로그 테이블에서 조회 (기존 메서드 호출)
            return getHourlyStats(date);
        }
    }

    public Map<String, Object> getSourceStats(LocalDateTime start, LocalDateTime end) {
        try {
            // 기본 시간 범위 설정 (기본: 최근 24시간)
            LocalDateTime endTime = end != null ? end : LocalDateTime.now();
            LocalDateTime startTime = start != null ? start : endTime.minusHours(24);

            // 소스별 로그 수 조회
            List<Object[]> sourceData = logRepository.findSourceStatsWithTimePeriod(startTime, endTime);

            List<Map<String, Object>> sourceStats = sourceData.stream()
                    .map(row -> {
                        Map<String, Object> stat = new HashMap<>();
                        stat.put("source", row[0]);
                        stat.put("count", row[1]);
                        return stat;
                    })
                    .sorted((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")))
                    .collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("startTime", startTime);
            result.put("endTime", endTime);
            result.put("sourceStats", sourceStats);
            return result;
        } catch (Exception e) {
            log.error("소스별 통계 조회 중 오류 발생", e);
            return Map.of(
                    "startTime", start,
                    "endTime", end,
                    "sourceStats", List.of()
            );
        }
    }

    public Map<String, Object> getSourceStatsFromStats(LocalDateTime start, LocalDateTime end) {
        try {
            // 날짜 범위 계산
            LocalDate startDate = start.toLocalDate();
            LocalDate endDate = end.toLocalDate();

            // 소스별 집계 조회
            List<Object[]> sourceData = logStatisticsRepository.findSourceStatsByDateRange(startDate, endDate);

            // 결과 변환
            List<Map<String, Object>> sourceStats = new ArrayList<>();

            for (Object[] row : sourceData) {
                Map<String, Object> stat = new HashMap<>();
                stat.put("source", row[0]);
                stat.put("count", ((Number) row[1]).longValue());
                sourceStats.add(stat);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("startTime", start);
            result.put("endTime", end);
            result.put("sourceStats", sourceStats);
            return result;
        } catch (Exception e) {
            log.error("통계 테이블에서 소스별 통계 조회 중 오류 발생", e);

            // 오류 발생 시 원본 로그 테이블에서 조회 (기존 메서드 호출)
            return getSourceStats(start, end);
        }
    }

    public SystemStatusResponse getSystemStatus() {
        try {
            // JVM 시작 시간으로부터 업타임 계산
            long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
            Duration uptime = Duration.ofMillis(uptimeMillis);

            long days = uptime.toDays();
            long hours = uptime.toHours() % 24;
            long minutes = uptime.toMinutes() % 60;

            String uptimeString = String.format("%dd %dh %dm", days, hours, minutes);

            // 메모리 정보
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;

            double memoryUsagePercent = (double) usedMemory / maxMemory * 100;

            // 실시간 메트릭 서비스에서 처리량, 오류율, 응답시간 가져오기
            long processedRate = systemMetricsService.getAverageProcessedRate();
            double errorRate = systemMetricsService.getAverageErrorRate();
            int avgResponseTime = systemMetricsService.getAverageResponseTime();

            // 시스템 상태 결정
            String status = determineSystemStatus(errorRate, avgResponseTime);

            return SystemStatusResponse.builder()
                    .uptime(uptimeString)
                    .memoryUsage(Math.round(memoryUsagePercent * 100.0) / 100.0)
                    .processedRate(processedRate)
                    .errorRate(Math.round(errorRate * 100.0) / 100.0)
                    .avgResponseTime(avgResponseTime)
                    .status(status)
                    .timestamp(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.error("시스템 상태 정보 조회 중 오류 발생", e);
            return SystemStatusResponse.builder()
                    .uptime("0d 0h 0m")
                    .memoryUsage(0.0)
                    .processedRate(0L)
                    .errorRate(0.0)
                    .avgResponseTime(0)
                    .status("ERROR")
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }

    private String determineSystemStatus(double errorRate, int avgResponseTime) {
        // 높은 오류율 또는 높은 응답 시간은 시스템 상태에 영향을 줌
        if (errorRate > 15.0 || avgResponseTime > 500) {
            return "DEGRADED";
        } else if (errorRate > 5.0 || avgResponseTime > 200) {
            return "WARNING";
        } else {
            return "UP";
        }
    }

    public Map<String, Object> getRecentErrors() {
        try {
            // 최근 오류 로그 조회 (최대 5개)
            List<LogEntry> errorLogs = logRepository.findByLogLevelOrderByCreatedAtDesc(
                    "ERROR", PageRequest.of(0, 5));

            List<LogEntryResponse> errorLogResponses = errorLogs.stream()
                    .map(LogEntryResponse::of)
                    .collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("timestamp", LocalDateTime.now());
            result.put("recentErrors", errorLogResponses);
            return result;
        } catch (Exception e) {
            log.error("최근 오류 로그 조회 중 오류 발생", e);
            return Map.of(
                    "timestamp", LocalDateTime.now(),
                    "recentErrors", List.of()
            );
        }
    }

    public Map<String, Object> getErrorTrends(LocalDateTime start, LocalDateTime end) {
        try {
            // 기본 시간 범위 설정 (기본: 최근 7일)
            LocalDateTime endTime = end != null ? end : LocalDateTime.now();
            LocalDateTime startTime = start != null ? start : endTime.minusDays(7);

            List<Map<String, Object>> dailyStats = new ArrayList<>();

            // 각 날짜별 데이터 수집
            LocalDate currentDate = startTime.toLocalDate();
            LocalDate endDate = endTime.toLocalDate();

            while (!currentDate.isAfter(endDate)) {
                LocalDateTime dayStart = currentDate.atStartOfDay();
                LocalDateTime dayEnd = currentDate.plusDays(1).atStartOfDay();

                // 해당 날짜의 전체 로그 수
                long totalLogs = logRepository.countByCreatedAtBetween(dayStart, dayEnd);

                // 해당 날짜의 오류 로그 수
                long errorLogs = logRepository.countByLogLevelAndCreatedAtBetween("ERROR", dayStart, dayEnd);

                // 오류율 계산
                double errorRate = totalLogs > 0 ? (double) errorLogs / totalLogs * 100 : 0;
                errorRate = Math.round(errorRate * 100.0) / 100.0; // 소수점 두 자리로 반올림

                Map<String, Object> dayStat = new HashMap<>();
                dayStat.put("date", currentDate.toString());
                dayStat.put("totalLogs", totalLogs);
                dayStat.put("errorLogs", errorLogs);
                dayStat.put("errorRate", errorRate);

                dailyStats.add(dayStat);

                // 다음 날짜로 이동
                currentDate = currentDate.plusDays(1);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("startDate", startTime.toLocalDate().toString());
            result.put("endDate", endTime.toLocalDate().toString());
            result.put("dailyStats", dailyStats);
            return result;
        } catch (Exception e) {
            log.error("오류 추세 데이터 조회 중 오류 발생", e);
            return Map.of(
                    "startDate", start != null ? start.toLocalDate().toString() : "",
                    "endDate", end != null ? end.toLocalDate().toString() : "",
                    "dailyStats", List.of()
            );
        }
    }
}