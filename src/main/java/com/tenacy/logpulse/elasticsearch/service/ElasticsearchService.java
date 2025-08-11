package com.tenacy.logpulse.elasticsearch.service;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.elasticsearch.document.LogDocument;
import com.tenacy.logpulse.elasticsearch.repository.LogDocumentRepository;
import com.tenacy.logpulse.service.LogCompressionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ElasticsearchService {

    private final LogDocumentRepository logDocumentRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final LogCompressionService compressionService;

    @Value("${logpulse.elasticsearch.bulk-size:1000}")
    private int bulkSize;

    @Value("${logpulse.elasticsearch.enabled:true}")
    private boolean elasticsearchEnabled;

    private boolean elasticsearchAvailable = true;

    public ElasticsearchService(LogDocumentRepository logDocumentRepository,
                                ElasticsearchOperations elasticsearchOperations,
                                LogCompressionService compressionService) {
        this.logDocumentRepository = logDocumentRepository;
        this.elasticsearchOperations = elasticsearchOperations;
        this.compressionService = compressionService;
    }

    public boolean isAvailable() {
        if (!elasticsearchEnabled) {
            return false;
        }

        if (!elasticsearchAvailable) {
            try {
                elasticsearchOperations.count(NativeQuery.builder().build(), LogDocument.class);
                elasticsearchAvailable = true;
                log.info("Elasticsearch 연결 복구됨");
            } catch (Exception e) {
                log.warn("Elasticsearch를 사용할 수 없음: {}", e.getMessage());
                return false;
            }
        }

        return true;
    }

    public void saveLog(LogEntry logEntry) {
        saveLog(logEntry, null);
    }

    public void saveLog(LogEntry logEntry, String explicitContent) {
        if (!isAvailable()) {
            log.debug("Elasticsearch를 사용할 수 없습니다. 로그 인덱싱 건너뜀: {}", logEntry.getId());
            return;
        }

        try {
            String contentToUse = explicitContent;

            // 명시적 콘텐츠가 없고 압축된 경우 압축 해제
            if (contentToUse == null) {
                contentToUse = logEntry.getContent();
                if (Boolean.TRUE.equals(logEntry.getCompressed()) && contentToUse != null) {
                    contentToUse = compressionService.decompressContent(contentToUse);
                }
            }

            // 최종적으로 사용할 콘텐츠로 LogDocument 생성
            LogDocument logDocument = createLogDocument(logEntry, contentToUse);
            logDocumentRepository.save(logDocument);

            log.debug("로그를 Elasticsearch에 저장: {}", logDocument.getId());

        } catch (Exception e) {
            log.error("로그를 Elasticsearch에 저장하는 중 오류 발생: {}", e.getMessage());
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

            for (int i = 0; i < totalSize; i += effectiveBulkSize) {
                int toIndex = Math.min(i + effectiveBulkSize, totalSize);

                List<LogDocument> chunk = logEntries.subList(i, toIndex).stream()
                        .map(entry -> {
                            String contentToUse = entry.getContent();
                            // 압축된 경우 압축 해제
                            if (Boolean.TRUE.equals(entry.getCompressed()) && contentToUse != null) {
                                contentToUse = compressionService.decompressContent(contentToUse);
                            }
                            return createLogDocument(entry, contentToUse);
                        })
                        .collect(Collectors.toList());

                logDocumentRepository.saveAll(chunk);
                log.debug("Elasticsearch에 {}개 로그 저장 완료", chunk.size());
            }

        } catch (Exception e) {
            log.error("로그를 Elasticsearch에 대량 저장하는 중 오류 발생: {}", e.getMessage());
            elasticsearchAvailable = false;
        }
    }

    private LogDocument createLogDocument(LogEntry entry, String content) {
        return LogDocument.builder()
                .id(entry.getId() != null ? entry.getId().toString() : java.util.UUID.randomUUID().toString())
                .source(entry.getSource())
                .content(content)
                .logLevel(entry.getLogLevel())
                .timestamp(entry.getCreatedAt())
                .build();
    }

    public List<LogDocument> searchWith(
            String keyword, String level, String source, String content,
            LocalDateTime start, LocalDateTime end, Pageable pageable) {

        if (!isAvailable()) {
            log.debug("Elasticsearch를 사용할 수 없습니다. 빈 검색 결과 반환");
            return Collections.emptyList();
        }

        try {
            BoolQuery.Builder b = new BoolQuery.Builder();

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

            co.elastic.clients.elasticsearch._types.query_dsl.Query query = b.build()._toQuery();

            // 최종 쿼리 생성
            NativeQuery searchQuery = new NativeQuery(query);

            // 페이징 설정
            searchQuery.setPageable(pageable);
            
            // 검색 실행
            SearchHits<LogDocument> searchHits = elasticsearchOperations.search(
                    searchQuery, LogDocument.class);

            // 결과 변환
            return searchHits.getSearchHits().stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Elasticsearch에서 복합 검색 중 오류 발생: {}", e.getMessage(), e);
            elasticsearchAvailable = false;
            return Collections.emptyList();
        }
    }
}