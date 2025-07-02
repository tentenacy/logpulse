package com.tenacy.logpulse.service;

import com.tenacy.logpulse.domain.LogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogCleanupService {

    private final LogRepository logRepository;

    @Value("${logpulse.cleanup.retention-days:90}")
    private int retentionDays;

    @Scheduled(cron = "0 0 2 * * ?") // 매일 새벽 2시에 실행
    @Transactional
    public void cleanupOldLogs() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);

        int deletedCount = logRepository.deleteLogEntriesOlderThan(threshold);

        log.info("Cleaned up {} log entries older than {} days", deletedCount, retentionDays);
    }
}