package com.tenacy.logpulse.api;

import com.tenacy.logpulse.api.dto.LogSearchResponse;
import com.tenacy.logpulse.elasticsearch.document.LogDocument;
import com.tenacy.logpulse.elasticsearch.service.ElasticsearchService;
import com.tenacy.logpulse.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/logs/search")
@RequiredArgsConstructor
@Slf4j
public class LogSearchController {

    private final ElasticsearchService elasticsearchService;
    private final LogService logService;

    @GetMapping
    public ResponseEntity<Page<LogSearchResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        try {
            if (elasticsearchService.isAvailable()) {
                List<LogDocument> logs = elasticsearchService.searchWith(
                        keyword, level, source, content, start, end, pageable);

                List<LogSearchResponse> responses = logs.stream()
                        .map(doc -> LogSearchResponse.builder()
                                .id(doc.getId())
                                .source(doc.getSource())
                                .content(doc.getContent())
                                .logLevel(doc.getLogLevel())
                                .timestamp(doc.getTimestamp())
                                .build())
                        .collect(Collectors.toList());

                return ResponseEntity.ok(
                        new PageImpl<>(responses, pageable, responses.size()));
            }
        } catch (Exception e) {
            log.error("Elasticsearch 검색 중 오류 발생: {}", e.getMessage());
        }

        // DB에서 검색
        log.info("데이터베이스 검색으로 대체");

        Page<LogSearchResponse> result = logService.retrieveLogsWith(
                        keyword, level, source, content, start, end, pageable)
                .map(dto -> LogSearchResponse.builder()
                        .id(dto.getId().toString())
                        .source(dto.getSource())
                        .content(dto.getContent())
                        .logLevel(dto.getLogLevel())
                        .timestamp(dto.getCreatedAt())
                        .build());

        return ResponseEntity.ok(result);
    }
}