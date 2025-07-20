package com.tenacy.logpulse.elasticsearch.service;

import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.elasticsearch.document.LogDocument;
import com.tenacy.logpulse.elasticsearch.repository.LogDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    public List<LogDocument> findAll() {
        if (!isAvailable()) {
            log.debug("Elasticsearch is not available. Returning empty result for findAll");
            return Collections.emptyList();
        }

        try {
            List<LogDocument> result = new ArrayList<>();
            logDocumentRepository.findAll().forEach(result::add);
            return result;
        } catch (Exception e) {
            log.error("Error finding all logs from Elasticsearch: {}", e.getMessage());
            elasticsearchAvailable = false;
            return Collections.emptyList();
        }
    }

    public List<LogDocument> findByLogLevel(String logLevel) {
        if (!isAvailable()) {
            log.debug("Elasticsearch is not available. Returning empty result for level: {}", logLevel);
            return Collections.emptyList();
        }

        try {
            return logDocumentRepository.findByLogLevel(logLevel);
        } catch (Exception e) {
            log.error("Error finding logs by level from Elasticsearch: {}", e.getMessage());
            elasticsearchAvailable = false;
            return Collections.emptyList();
        }
    }

    public List<LogDocument> findBySourceContaining(String source) {
        if (!isAvailable()) {
            log.debug("Elasticsearch is not available. Returning empty result for source: {}", source);
            return Collections.emptyList();
        }

        try {
            return logDocumentRepository.findBySourceContaining(source);
        } catch (Exception e) {
            log.error("Error finding logs by source from Elasticsearch: {}", e.getMessage());
            elasticsearchAvailable = false;
            return Collections.emptyList();
        }
    }

    public List<LogDocument> findByContentContaining(String content) {
        if (!isAvailable()) {
            log.debug("Elasticsearch is not available. Returning empty result for content: {}", content);
            return Collections.emptyList();
        }

        try {
            return logDocumentRepository.findByContentContaining(content);
        } catch (Exception e) {
            log.error("Error finding logs by content from Elasticsearch: {}", e.getMessage());
            elasticsearchAvailable = false;
            return Collections.emptyList();
        }
    }

    public List<LogDocument> findByTimestampBetween(LocalDateTime start, LocalDateTime end) {
        if (!isAvailable()) {
            log.debug("Elasticsearch is not available. Returning empty result for period: {} to {}", start, end);
            return Collections.emptyList();
        }

        try {
            return logDocumentRepository.findByTimestampBetween(start, end);
        } catch (Exception e) {
            log.error("Error finding logs by timestamp range from Elasticsearch: {}", e.getMessage());
            elasticsearchAvailable = false;
            return Collections.emptyList();
        }
    }

    public List<LogDocument> searchByKeyword(String keyword) {
        if (!isAvailable()) {
            log.debug("Elasticsearch is not available. Returning empty result for keyword: {}", keyword);
            return Collections.emptyList();
        }

        try {
            // 개선된 검색 쿼리
            Query searchQuery = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> b
                            .should(s -> s.match(m -> m
                                    .field("content")
                                    .query(keyword)))
                            .should(s -> s.match(m -> m
                                    .field("content.ngram")
                                    .query(keyword)))
                            .minimumShouldMatch("1")))
                    .withPageable(PageRequest.of(0, 100))
                    .build();

            SearchHits<LogDocument> searchHits = elasticsearchOperations.search(searchQuery, LogDocument.class);

            return searchHits.getSearchHits().stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error searching logs by keyword from Elasticsearch: {}", e.getMessage());
            elasticsearchAvailable = false;
            return Collections.emptyList();
        }
    }
}