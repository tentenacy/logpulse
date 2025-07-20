package com.tenacy.logpulse.api;

import com.tenacy.logpulse.api.dto.LogEntryRequest;
import com.tenacy.logpulse.api.dto.LogEntryResponse;
import com.tenacy.logpulse.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    @PostMapping
    public ResponseEntity<LogEntryResponse> createLog(@RequestBody LogEntryRequest request) {
        return ResponseEntity.ok(logService.createLog(request));
    }

    @GetMapping
    public ResponseEntity<Page<LogEntryResponse>> getAllLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        return ResponseEntity.ok(logService.retrieveLogsWithFilters(level, source, start, end, pageable));
    }
}