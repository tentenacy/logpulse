package com.tenacy.logpulse.service;

import com.tenacy.logpulse.api.dto.LogEntryRequest;
import com.tenacy.logpulse.api.dto.LogEntryResponse;
import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.domain.LogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LogService {

    private final LogRepository logRepository;

    @Transactional
    public LogEntryResponse createLog(LogEntryRequest request) {
        LogEntry logEntry = LogEntry.builder()
                .source(request.getSource())
                .content(request.getContent())
                .logLevel(request.getLogLevel())
                .build();

        LogEntry savedEntry = logRepository.save(logEntry);
        return LogEntryResponse.of(savedEntry);
    }

    public List<LogEntryResponse> retrieveAllLogs() {
        return logRepository.findAll().stream()
                .map(LogEntryResponse::of)
                .collect(Collectors.toList());
    }

    public List<LogEntryResponse> retrieveLogsByLevel(String level) {
        return logRepository.findByLogLevel(level).stream()
                .map(LogEntryResponse::of)
                .collect(Collectors.toList());
    }

    public List<LogEntryResponse> retrieveLogsBetween(LocalDateTime start, LocalDateTime end) {
        return logRepository.findByCreatedAtBetween(start, end).stream()
                .map(LogEntryResponse::of)
                .collect(Collectors.toList());
    }
}
