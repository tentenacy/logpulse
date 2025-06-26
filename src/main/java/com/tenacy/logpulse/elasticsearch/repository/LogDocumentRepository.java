package com.tenacy.logpulse.elasticsearch.repository;

import com.tenacy.logpulse.elasticsearch.document.LogDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LogDocumentRepository extends ElasticsearchRepository<LogDocument, String> {

    List<LogDocument> findByLogLevel(String logLevel);

    List<LogDocument> findBySourceContaining(String source);

    List<LogDocument> findByContentContaining(String content);

    List<LogDocument> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
}