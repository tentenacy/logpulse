package com.tenacy.logpulse.service;

import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.domain.LogStatistics;
import com.tenacy.logpulse.domain.LogStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogStatisticsService {

    private final LogStatisticsRepository logStatisticsRepository;

    /**
     * 로그 생성 시 통계 테이블 업데이트
     */
    @Transactional
    public void updateStatistics(String source, String logLevel, LocalDateTime timestamp) {
        if (source == null || logLevel == null || timestamp == null) {
            log.warn("통계 업데이트를 위한 유효하지 않은 로그 데이터: source={}, level={}, timestamp={}",
                    source, logLevel, timestamp);
            return;
        }

        LocalDate logDate = timestamp.toLocalDate();
        int hour = timestamp.getHour();

        try {
            // 기존 통계 레코드가 있는지 확인하고 count 증가
            int updatedRows = logStatisticsRepository.incrementCount(logDate, hour, source, logLevel);

            // 기존 레코드가 없으면 새로 생성
            if (updatedRows == 0) {
                LogStatistics newStat = LogStatistics.builder()
                        .logDate(logDate)
                        .hour(hour)
                        .source(source)
                        .logLevel(logLevel)
                        .count(1)
                        .updatedAt(LocalDateTime.now())
                        .build();

                logStatisticsRepository.save(newStat);
                log.debug("새 통계 레코드 생성: date={}, hour={}, source={}, level={}",
                        logDate, hour, source, logLevel);
            } else {
                log.debug("기존 통계 레코드 업데이트: date={}, hour={}, source={}, level={}",
                        logDate, hour, source, logLevel);
            }
        } catch (Exception e) {
            log.error("통계 업데이트 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * 로그 배치 처리 시 통계 대량 업데이트
     */
    @Transactional
    public void batchUpdateStatistics(List<LogEntry> logEntries) {
        Map<StatKey, Integer> statCounts = new HashMap<>();

        // 로그 항목 집계
        for (LogEntry entry : logEntries) {
            LocalDate logDate = entry.getCreatedAt().toLocalDate();
            int hour = entry.getCreatedAt().getHour();
            String source = entry.getSource();
            String logLevel = entry.getLogLevel();

            StatKey key = new StatKey(logDate, hour, source, logLevel);
            statCounts.merge(key, 1, Integer::sum);
        }

        // 집계된 통계 업데이트
        for (Map.Entry<StatKey, Integer> entry : statCounts.entrySet()) {
            StatKey key = entry.getKey();
            int count = entry.getValue();

            LogStatistics stat = logStatisticsRepository.findByLogDateAndSourceAndLogLevelAndHour(
                    key.logDate, key.source, key.logLevel, key.hour);

            if (stat != null) {
                // 기존 레코드 업데이트
                stat.setCount(stat.getCount() + count);
                stat.setUpdatedAt(LocalDateTime.now());
                logStatisticsRepository.save(stat);
            } else {
                // 새 레코드 생성
                LogStatistics newStat = LogStatistics.builder()
                        .logDate(key.logDate)
                        .hour(key.hour)
                        .source(key.source)
                        .logLevel(key.logLevel)
                        .count(count)
                        .updatedAt(LocalDateTime.now())
                        .build();

                logStatisticsRepository.save(newStat);
            }
        }
    }

    // 통계 키 (복합 키)
    private static class StatKey {
        private final LocalDate logDate;
        private final Integer hour;
        private final String source;
        private final String logLevel;

        public StatKey(LocalDate logDate, Integer hour, String source, String logLevel) {
            this.logDate = logDate;
            this.hour = hour;
            this.source = source;
            this.logLevel = logLevel;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StatKey statKey = (StatKey) o;
            return Objects.equals(logDate, statKey.logDate) &&
                    Objects.equals(hour, statKey.hour) &&
                    Objects.equals(source, statKey.source) &&
                    Objects.equals(logLevel, statKey.logLevel);
        }

        @Override
        public int hashCode() {
            return Objects.hash(logDate, hour, source, logLevel);
        }
    }
}