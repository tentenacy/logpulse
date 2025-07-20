package com.tenacy.logpulse.api;

import com.tenacy.logpulse.api.dto.LogSearchResponse;
import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.domain.LogRepository;
import com.tenacy.logpulse.elasticsearch.document.LogDocument;
import com.tenacy.logpulse.elasticsearch.service.ElasticsearchService;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/v1/logs/search")
@RequiredArgsConstructor
@Slf4j
public class LogSearchController {

    private final ElasticsearchService elasticsearchService;
    private final LogRepository logRepository;

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
        Pageable elasticsearchPageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        try {
            if (elasticsearchService.isAvailable()) {
                List<LogDocument> logs;

                if (keyword != null && !keyword.trim().isEmpty()) {
                    logs = elasticsearchService.searchByKeyword(keyword.trim());
                } else if (start != null && end != null) {
                    logs = elasticsearchService.findByTimestampBetween(start, end);
                } else if (level != null) {
                    logs = elasticsearchService.findByLogLevel(level);
                } else if (source != null) {
                    logs = elasticsearchService.findBySourceContaining(source);
                } else if (content != null) {
                    logs = elasticsearchService.findByContentContaining(content);
                } else {
                    logs = elasticsearchService.findAll();
                }

                Page<LogSearchResponse> result = convertToPage(logs, elasticsearchPageable);
                return ResponseEntity.ok(result);
            }
        } catch (Exception e) {
            log.error("Error in unified search in Elasticsearch: {}", e.getMessage());
        }

        Pageable jpaPageable = convertToJpaCompatiblePageable(elasticsearchPageable);
        log.info("Falling back to database search with multiple criteria");
        Page<LogEntry> logEntries;

        if (keyword != null && !keyword.trim().isEmpty()) {
            logEntries = logRepository.findByContentContainingIgnoreCaseOrSourceContainingIgnoreCase(
                    keyword.trim(), keyword.trim(), jpaPageable);
        } else if (start != null && end != null) {
            logEntries = logRepository.findByCreatedAtBetween(start, end, jpaPageable);
        } else if (level != null) {
            logEntries = logRepository.findByLogLevel(level, jpaPageable);
        } else if (source != null) {
            logEntries = logRepository.findBySourceContaining(source, jpaPageable);
        } else if (content != null) {
            logEntries = logRepository.findByContentContaining(content, jpaPageable);
        } else {
            logEntries = logRepository.findAll(jpaPageable);
        }

        Page<LogSearchResponse> result = LogSearchResponse.pageOf(logEntries);
        return ResponseEntity.ok(result);
    }

    private Page<LogSearchResponse> convertToPage(List<LogDocument> logs, Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            Sort.Order order = pageable.getSort().iterator().next();
            boolean ascending = order.getDirection() == Sort.Direction.ASC;

            if ("timestamp".equals(order.getProperty())) {
                logs.sort((a, b) -> ascending ?
                        a.getTimestamp().compareTo(b.getTimestamp()) :
                        b.getTimestamp().compareTo(a.getTimestamp()));
            } else if ("logLevel".equals(order.getProperty())) {
                logs.sort((a, b) -> ascending ?
                        a.getLogLevel().compareTo(b.getLogLevel()) :
                        b.getLogLevel().compareTo(a.getLogLevel()));
            } else if ("source".equals(order.getProperty())) {
                logs.sort((a, b) -> ascending ?
                        a.getSource().compareTo(b.getSource()) :
                        b.getSource().compareTo(a.getSource()));
            }
        }

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), logs.size());

        if (start >= logs.size()) {
            return new PageImpl<>(Collections.emptyList(), pageable, logs.size());
        }

        List<LogDocument> pagedLogs = logs.subList(start, end);
        List<LogSearchResponse> responses = LogSearchResponse.listOf(pagedLogs);

        return new PageImpl<>(responses, pageable, logs.size());
    }

    private Pageable convertToJpaCompatiblePageable(Pageable elasticsearchPageable) {
        if (elasticsearchPageable.getSort().isEmpty()) {
            return elasticsearchPageable;
        }

        List<Sort.Order> convertedOrders = new ArrayList<>();

        for (Sort.Order order : elasticsearchPageable.getSort()) {
            String property = order.getProperty();

            if ("timestamp".equals(property)) {
                property = "createdAt";
            }

            convertedOrders.add(new Sort.Order(order.getDirection(), property));
        }

        return PageRequest.of(
                elasticsearchPageable.getPageNumber(),
                elasticsearchPageable.getPageSize(),
                Sort.by(convertedOrders)
        );
    }
}