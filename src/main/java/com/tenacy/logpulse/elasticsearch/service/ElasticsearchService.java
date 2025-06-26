package com.tenacy.logpulse.elasticsearch.service;

import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.elasticsearch.document.LogDocument;
import com.tenacy.logpulse.elasticsearch.repository.LogDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public void saveLog(LogEntry logEntry) {
        LogDocument logDocument = LogDocument.fromLogEntry(logEntry);
        logDocumentRepository.save(logDocument);
        log.debug("Saved log to Elasticsearch: {}", logDocument);
    }

    public void saveAll(List<LogEntry> logEntries) {
        List<LogDocument> logDocuments = logEntries.stream()
                .map(LogDocument::fromLogEntry)
                .collect(Collectors.toList());
        logDocumentRepository.saveAll(logDocuments);
        log.debug("Saved {} logs to Elasticsearch", logDocuments.size());
    }

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
}