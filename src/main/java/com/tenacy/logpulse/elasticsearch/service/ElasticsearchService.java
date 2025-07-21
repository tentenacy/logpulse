package com.tenacy.logpulse.elasticsearch.service;

import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.elasticsearch.document.LogDocument;
import com.tenacy.logpulse.elasticsearch.repository.LogDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchService {

    private final LogDocumentRepository logDocumentRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    @Value("${logpulse.elasticsearch.bulk-size:1000}")
    private int bulkSize;

    @Value("${logpulse.elasticsearch.enabled:true}")
    private boolean elasticsearchEnabled;

    private boolean elasticsearchAvailable = true;

    public boolean isAvailable() {
        if (!elasticsearchEnabled) {
            return false;
        }

        if (!elasticsearchAvailable) {
            try {
                // 간단한 검색으로 Elasticsearch 연결 확인
                elasticsearchOperations.count(NativeQuery.builder().build(), LogDocument.class);
                elasticsearchAvailable = true;
                log.info("Elasticsearch connection restored");
            } catch (Exception e) {
                log.warn("Elasticsearch is still not available: {}", e.getMessage());
                return false;
            }
        }

        return true;
    }

    public void saveLog(LogEntry logEntry) {
        if (!isAvailable()) {
            log.debug("Elasticsearch is not available. Skipping save operation for log: {}", logEntry.getId());
            return;
        }

        try {
            LogDocument logDocument = LogDocument.of(logEntry);
            logDocumentRepository.save(logDocument);
            log.debug("Saved log to Elasticsearch: {}", logDocument);
        } catch (Exception e) {
            log.error("Failed to save log to Elasticsearch: {}", e.getMessage());
            elasticsearchAvailable = false;
        }
    }

    public void saveAll(List<LogEntry> logEntries) {
        if (!isAvailable() || logEntries == null || logEntries.isEmpty()) {
            return;
        }

        try {
            // 벌크 처리를 위해 청크로 분할
            int totalSize = logEntries.size();
            int effectiveBulkSize = (bulkSize <= 0) ? 1000 : bulkSize;
            int chunkCount = (totalSize + effectiveBulkSize - 1) / effectiveBulkSize;

            for (int i = 0; i < chunkCount; i++) {
                int fromIndex = i * effectiveBulkSize;
                int toIndex = Math.min(fromIndex + effectiveBulkSize, totalSize);

                List<LogDocument> chunk = logEntries.subList(fromIndex, toIndex).stream()
                        .map(LogDocument::of)
                        .collect(Collectors.toList());

                logDocumentRepository.saveAll(chunk);
                log.debug("Saved chunk of {} logs to Elasticsearch (chunk {}/{})",
                        chunk.size(), i+1, chunkCount);
            }
        } catch (Exception e) {
            log.error("Failed to save logs to Elasticsearch in bulk: {}", e.getMessage());
            elasticsearchAvailable = false;
        }
    }

    public List<LogDocument> searchWith(
            String keyword, String level, String source, String content,
            LocalDateTime start, LocalDateTime end, Pageable pageable) {

        if (!isAvailable()) {
            log.debug("Elasticsearch is not available. Returning empty result for complex search");
            return Collections.emptyList();
        }

        try {
            NativeQueryBuilder queryBuilder = NativeQuery.builder();

            queryBuilder.withQuery(q -> {
                return q.bool(b -> {
                    if (keyword != null && !keyword.trim().isEmpty()) {
                        String trimmedKeyword = keyword.trim();
                        b.should(s -> s.match(m -> m.field("content").query(trimmedKeyword)));
                        b.should(s -> s.match(m -> m.field("content.ngram").query(trimmedKeyword)));
                        b.minimumShouldMatch("1");
                    }

                    if (level != null && !level.trim().isEmpty()) {
                        b.must(m -> m.term(t -> t.field("logLevel").value(level.trim())));
                    }

                    if (source != null && !source.trim().isEmpty()) {
                        b.must(m -> m.wildcard(w -> w.field("source").value("*" + source.trim() + "*")));
                    }

                    if (content != null && !content.trim().isEmpty()) {
                        b.must(m -> m.wildcard(w -> w.field("content").value("*" + content.trim() + "*")));
                    }

                    if (start != null && end != null) {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
                        String startStr = start.format(formatter);
                        String endStr = end.format(formatter);

                        b.must(m -> m.range(r -> r
                                .date(t -> t
                                        .field("timestamp")
                                        .gte(startStr)
                                        .lte(endStr))));
                    }

                    if (keyword == null && level == null && source == null &&
                            content == null && (start == null || end == null)) {
                        b.must(m -> m.matchAll(ma -> ma));
                    }

                    return b;
                });
            });

            // 페이징 설정
            queryBuilder.withPageable(pageable);

            // 최종 쿼리 생성
            Query searchQuery = queryBuilder.build();

            // 검색 실행
            SearchHits<LogDocument> searchHits = elasticsearchOperations.search(
                    searchQuery, LogDocument.class);

            // 결과 반환
            return searchHits.getSearchHits().stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error performing complex search in Elasticsearch: {}", e.getMessage());
            elasticsearchAvailable = false;
            return Collections.emptyList();
        }
    }
}