package com.tenacy.logpulse.api;

import com.tenacy.logpulse.api.dto.LogEntryRequest;
import com.tenacy.logpulse.api.dto.LogEntryResponse;
import com.tenacy.logpulse.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

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
    public ResponseEntity<List<LogEntryResponse>> getAllLogs() {
        return ResponseEntity.ok(logService.retrieveAllLogs());
    }

    @GetMapping("/level/{level}")
    public ResponseEntity<List<LogEntryResponse>> getLogsByLevel(@PathVariable String level) {
        return ResponseEntity.ok(logService.retrieveLogsByLevel(level));
    }

    @GetMapping("/period")
    public ResponseEntity<List<LogEntryResponse>> getLogsBetween(
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(logService.retrieveLogsBetween(start, end));
    }
}