package com.tenacy.logpulse.service;

import com.tenacy.logpulse.api.dto.DashboardStatsResponse;
import com.tenacy.logpulse.api.dto.LogCountResponse;
import com.tenacy.logpulse.api.dto.LogEntryResponse;
import com.tenacy.logpulse.api.dto.SystemStatusResponse;
import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.domain.LogRepository;
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

    public DashboardStatsResponse getDashboardStats(LocalDateTime start, LocalDateTime end, String source) {
        // 기본 시간 범위 설정 (기본: 최근 24시간)
        LocalDateTime endTime = end != null ? end : LocalDateTime.now();
        LocalDateTime startTime = start != null ? start : endTime.minusHours(24);

        // 로그 레벨별 카운트
        LogCountResponse logCounts = getLogCounts(startTime, endTime, source);

        // 시간별 통계
        Map<String, Object> hourlyStats = getHourlyStats(LocalDate.now());

        // 소스별 통계
        Map<String, Object> sourceStats = getSourceStats(startTime, endTime);

        // 시스템 상태
        SystemStatusResponse systemStatus = getSystemStatus();

        // 최근 오류 로그
        Map<String, Object> recentErrors = getRecentErrors();

        return DashboardStatsResponse.builder()
                .logCounts(logCounts)
                .hourlyStats(hourlyStats)
                .sourceStats(sourceStats)
                .systemStatus(systemStatus)
                .recentErrors(recentErrors)
                .timestamp(LocalDateTime.now())
                .build();
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

            // 최근 24시간 로그 기반 통계
            LocalDateTime oneDayAgo = LocalDateTime.now().minusHours(24);
            long totalLogs = logRepository.countByCreatedAtAfter(oneDayAgo);
            long errorLogs = logRepository.countByLogLevelAndCreatedAtAfter("ERROR", oneDayAgo);

            // 오류율 계산
            double errorRate = totalLogs > 0 ? (double) errorLogs / totalLogs * 100 : 0;

            return SystemStatusResponse.builder()
                    .uptime(uptimeString)
                    .memoryUsage(Math.round(memoryUsagePercent * 100.0) / 100.0)
                    .processedRate(totalLogs / 24)
                    .errorRate(Math.round(errorRate * 100.0) / 100.0)
                    .avgResponseTime(0)
                    .status("UP")
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
}