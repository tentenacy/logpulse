package com.tenacy.logpulse.service;

import com.tenacy.logpulse.api.dto.LogEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class FileLogCollectorService {

    private final IntegrationLogService integrationLogService;

    @Value("${logpulse.collector.directory}")
    private String logsDirectory;

    @Value("${logpulse.collector.file-pattern}")
    private String filePattern;

    private final Map<String, Long> filePositions = new HashMap<>();

    @Scheduled(fixedDelay = 5000) // 5초마다 실행
    public void collectLogs() {
        Path dir = Paths.get(logsDirectory);

        // 디렉토리가 없으면 생성
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
                log.info("로그 디렉토리 생성: {}", dir);
            } catch (IOException e) {
                log.error("로그 디렉토리 생성 실패: {}", dir, e);
                return;
            }
        }

        // 파일 패턴에 맞는 로그 파일 스캔
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, filePattern)) {
            for (Path path : stream) {
                processLogFile(path);
            }
        } catch (IOException e) {
            log.error("로그 디렉토리 스캔 실패: {}", dir, e);
        }
    }

    private void processLogFile(Path path) {
        String fileName = path.toString();
        long position = filePositions.getOrDefault(fileName, 0L);
        long fileSize;

        try {
            fileSize = Files.size(path);
            if (fileSize > position) {
                try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(fileName, "r")) {
                    file.seek(position);
                    String line;
                    while ((line = file.readLine()) != null) {
                        // 로그 파싱 및 처리
                        processLogLine(path.getFileName().toString(), line);
                        position = file.getFilePointer();
                    }
                }
                filePositions.put(fileName, position);
                log.debug("로그 파일 처리: {}, 새 위치: {}", fileName, position);
            }
        } catch (IOException e) {
            log.error("로그 파일 처리 실패: {}", fileName, e);
        }
    }

    private void processLogLine(String source, String line) {
        // 로그 레벨 추출
        String logLevel = "INFO";
        if (line.contains("ERROR")) {
            logLevel = "ERROR";
        } else if (line.contains("WARN")) {
            logLevel = "WARN";
        } else if (line.contains("DEBUG")) {
            logLevel = "DEBUG";
        }

        LogEventDto logEventDto = LogEventDto.builder()
                .source(source)
                .content(line)
                .logLevel(logLevel)
                .timestamp(LocalDateTime.now())
                .build();

        // 로그 처리 파이프라인으로 전달
        integrationLogService.processLog(logEventDto);
    }
}