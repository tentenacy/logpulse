package com.tenacy.logpulse.api;

import com.tenacy.logpulse.api.dto.LogSearchResponse;
import com.tenacy.logpulse.elasticsearch.document.LogDocument;
import com.tenacy.logpulse.elasticsearch.service.ElasticsearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/logs/search")
@RequiredArgsConstructor
public class LogSearchController {

    private final ElasticsearchService elasticsearchService;

    @GetMapping("/level/{level}")
    public ResponseEntity<List<LogSearchResponse>> searchByLogLevel(@PathVariable String level) {
        List<LogDocument> logs = elasticsearchService.findByLogLevel(level);
        return ResponseEntity.ok(convertToResponse(logs));
    }

    @GetMapping("/source")
    public ResponseEntity<List<LogSearchResponse>> searchBySource(@RequestParam String source) {
        List<LogDocument> logs = elasticsearchService.findBySourceContaining(source);
        return ResponseEntity.ok(convertToResponse(logs));
    }

    @GetMapping("/content")
    public ResponseEntity<List<LogSearchResponse>> searchByContent(@RequestParam String content) {
        List<LogDocument> logs = elasticsearchService.findByContentContaining(content);
        return ResponseEntity.ok(convertToResponse(logs));
    }

    @GetMapping("/period")
    public ResponseEntity<List<LogSearchResponse>> searchByPeriod(
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        List<LogDocument> logs = elasticsearchService.findByTimestampBetween(start, end);
        return ResponseEntity.ok(convertToResponse(logs));
    }

    @GetMapping("/keyword")
    public ResponseEntity<List<LogSearchResponse>> searchByKeyword(@RequestParam String keyword) {
        List<LogDocument> logs = elasticsearchService.searchByKeyword(keyword);
        return ResponseEntity.ok(convertToResponse(logs));
    }

    private List<LogSearchResponse> convertToResponse(List<LogDocument> logs) {
        return logs.stream()
                .map(log -> LogSearchResponse.builder()
                        .id(log.getId())
                        .source(log.getSource())
                        .content(log.getContent())
                        .logLevel(log.getLogLevel())
                        .timestamp(log.getTimestamp())
                        .build())
                .collect(Collectors.toList());
    }
}