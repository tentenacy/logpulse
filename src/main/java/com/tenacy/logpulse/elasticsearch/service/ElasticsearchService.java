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

    public void saveLog(LogEntry logEntry) {
        LogDocument logDocument = LogDocument.of(logEntry);
        logDocumentRepository.save(logDocument);
        log.debug("Saved log to Elasticsearch: {}", logDocument);
    }

    public void saveAll(List<LogEntry> logEntries) {
        if (logEntries.isEmpty()) {
            return;
        }

        // 벌크 처리를 위해 청크로 분할
        int totalSize = logEntries.size();
        int chunkCount = (totalSize + bulkSize - 1) / bulkSize;

        for (int i = 0; i < chunkCount; i++) {
            int fromIndex = i * bulkSize;
            int toIndex = Math.min(fromIndex + bulkSize, totalSize);

            List<LogDocument> chunk = logEntries.subList(fromIndex, toIndex).stream()
                    .map(LogDocument::of)
                    .collect(Collectors.toList());

            logDocumentRepository.saveAll(chunk);
            log.debug("Saved chunk of {} logs to Elasticsearch (chunk {}/{})",
                    chunk.size(), i+1, chunkCount);
        }
    }

    // 기존 메소드들 유지
    public List<LogDocument> findByLogLevel(String logLevel) {
        return logDocumentRepository.findByLogLevel(logLevel);
    }

    public List<LogDocument> findBySourceContaining(String source) {
        return logDocumentRepository.findBySourceContaining(source);
    }

    public List<LogDocument> findByContentContaining(String content) {
        return logDocumentRepository.findByContentContaining(content);
    }

    public List<LogDocument> findByTimestampBetween(LocalDateTime start, LocalDateTime end) {
        return logDocumentRepository.findByTimestampBetween(start, end);
    }

    public List<LogDocument> searchByKeyword(String keyword) {
        Query searchQuery = NativeQuery.builder()
                .withQuery(q -> q.multiMatch(m -> m
                        .query(keyword)
                        .fields("content", "source")))
                .withPageable(PageRequest.of(0, 20))
                .build();

        SearchHits<LogDocument> searchHits = elasticsearchOperations.search(searchQuery, LogDocument.class);

        return searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
    }

    // Elasticsearch 인덱스 최적화
    public void optimizeIndex() {
        log.info("Optimizing Elasticsearch index for logs");
        // 실제 구현은 ElasticsearchOperations를 사용하여 구현
    }
}