package com.tenacy.logpulse.batch;

import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.domain.LogStatistics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class LogStatisticsProcessor implements ItemProcessor<LogEntry, LogStatistics> {

    private final Map<String, Map<String, Integer>> statsMap = new HashMap<>();

    @Override
    public LogStatistics process(LogEntry item) {
        String source = item.getSource();
        String logLevel = item.getLogLevel();
        LocalDate logDate = item.getCreatedAt().toLocalDate();

        // 소스별, 로그 레벨별 카운트 집계
        statsMap.computeIfAbsent(source, k -> new HashMap<>())
                .merge(logLevel, 1, Integer::sum);

        // 통계 객체 생성
        LogStatistics stats = new LogStatistics();
        stats.setLogDate(logDate);
        stats.setSource(source);
        stats.setLogLevel(logLevel);
        stats.setCount(statsMap.get(source).get(logLevel));

        return stats;
    }
}