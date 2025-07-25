package com.tenacy.logpulse.elasticsearch.service;

import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.elasticsearch.document.LogDocument;
import com.tenacy.logpulse.elasticsearch.repository.LogDocumentRepository;
import com.tenacy.logpulse.service.LogCompressionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ElasticsearchService {

    private final LogDocumentRepository logDocumentRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final LogCompressionService compressionService;
    private final MeterRegistry meterRegistry;

    private final Queue<LogEntry> indexingQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean asyncIndexingInProgress = new AtomicBoolean(false);

    private final Counter documentsIndexedCounter;
    private final Counter indexingErrorsCounter;
    private final Counter searchRequestsCounter;
    private final Counter searchErrorsCounter;

    private final AtomicInteger queueSizeGauge = new AtomicInteger(0);
    private final AtomicLong lastIndexingTimeGauge = new AtomicLong(0);
    private final AtomicLong totalDocsIndexedGauge = new AtomicLong(0);

    @Value("${logpulse.elasticsearch.bulk-size:1000}")
    private int bulkSize;

    @Value("${logpulse.elasticsearch.enabled:true}")
    private boolean elasticsearchEnabled;

    @Value("${logpulse.elasticsearch.async-indexing-batch-size:200}")
    private int asyncIndexingBatchSize;

    @Value("${logpulse.elasticsearch.indexing-throttle-ms:100}")
    private int indexingThrottleMs;

    private AtomicBoolean elasticsearchAvailable = new AtomicBoolean(true);

    public ElasticsearchService(LogDocumentRepository logDocumentRepository,
                                ElasticsearchOperations elasticsearchOperations,
                                LogCompressionService compressionService,
                                MeterRegistry meterRegistry) {
        this.logDocumentRepository = logDocumentRepository;
        this.elasticsearchOperations = elasticsearchOperations;
        this.compressionService = compressionService;
        this.meterRegistry = meterRegistry;

        // 메트릭 등록
        this.documentsIndexedCounter = Counter.builder("logpulse.elasticsearch.documents.indexed")
                .description("Number of documents indexed in Elasticsearch")
                .register(meterRegistry);

        this.indexingErrorsCounter = Counter.builder("logpulse.elasticsearch.errors")
                .tag("operation", "indexing")
                .description("Number of Elasticsearch indexing errors")
                .register(meterRegistry);

        this.searchRequestsCounter = Counter.builder("logpulse.elasticsearch.search.requests")
                .description("Number of Elasticsearch search requests")
                .register(meterRegistry);

        this.searchErrorsCounter = Counter.builder("logpulse.elasticsearch.errors")
                .tag("operation", "search")
                .description("Number of Elasticsearch search errors")
                .register(meterRegistry);

        // 게이지 등록
        meterRegistry.gauge("logpulse.elasticsearch.indexing.queue.size", queueSizeGauge, AtomicInteger::get);
        meterRegistry.gauge("logpulse.elasticsearch.indexing.time", lastIndexingTimeGauge, AtomicLong::get);
        meterRegistry.gauge("logpulse.elasticsearch.documents.total", totalDocsIndexedGauge, AtomicLong::get);
    }

    public boolean isAvailable() {
        if (!elasticsearchEnabled) {
            return false;
        }

        if (!elasticsearchAvailable.get()) {
            try {
                // 간단한 검색으로 Elasticsearch 연결 확인
                elasticsearchOperations.count(NativeQuery.builder().build(), LogDocument.class);
                elasticsearchAvailable.set(true);
                log.info("Elasticsearch connection restored");
            } catch (Exception e) {
                log.warn("Elasticsearch is still not available: {}", e.getMessage());
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
            log.debug("Elasticsearch is not available. Queueing log for later indexing: {}", logEntry.getId());
            indexingQueue.add(logEntry);
            queueSizeGauge.set(indexingQueue.size());
            return;
        }

        Timer.Sample indexingTimer = Timer.start(meterRegistry);

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
            documentsIndexedCounter.increment();
            totalDocsIndexedGauge.incrementAndGet();

            log.debug("Saved log to Elasticsearch: {}", logDocument.getId());

            indexingTimer.stop(meterRegistry.timer("logpulse.elasticsearch.indexing.time",
                    "type", "single"));

        } catch (Exception e) {
            indexingErrorsCounter.increment();
            log.error("Failed to save log to Elasticsearch: {}", e.getMessage());
            elasticsearchAvailable.set(false);

            // 실패한 로그는 큐에 추가
            indexingQueue.add(logEntry);
            queueSizeGauge.set(indexingQueue.size());

            indexingTimer.stop(meterRegistry.timer("logpulse.elasticsearch.indexing.time",
                    "type", "single",
                    "result", "error"));
        }
    }

    public void saveAll(List<LogEntry> logEntries) {
        if (!isAvailable() || logEntries == null || logEntries.isEmpty()) {
            if (logEntries != null && !logEntries.isEmpty()) {
                // Elasticsearch를 사용할 수 없는 경우 큐에 추가
                indexingQueue.addAll(logEntries);
                queueSizeGauge.set(indexingQueue.size());
                log.debug("Elasticsearch unavailable. Queued {} logs for later indexing", logEntries.size());
            }
            return;
        }

        Timer.Sample batchIndexingTimer = Timer.start(meterRegistry);
        long startTime = System.currentTimeMillis();

        try {
            // 벌크 처리를 위해 청크로 분할
            int totalSize = logEntries.size();
            int effectiveBulkSize = (bulkSize <= 0) ? 1000 : bulkSize;
            int chunkCount = (totalSize + effectiveBulkSize - 1) / effectiveBulkSize;

            for (int i = 0; i < chunkCount; i++) {
                int fromIndex = i * effectiveBulkSize;
                int toIndex = Math.min(fromIndex + effectiveBulkSize, totalSize);

                List<LogDocument> chunk = logEntries.subList(fromIndex, toIndex).stream()
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
                documentsIndexedCounter.increment(chunk.size());
                totalDocsIndexedGauge.addAndGet(chunk.size());

                log.debug("Saved chunk of {} logs to Elasticsearch (chunk {}/{})",
                        chunk.size(), i+1, chunkCount);
            }

            long endTime = System.currentTimeMillis();
            lastIndexingTimeGauge.set(endTime - startTime);

            batchIndexingTimer.stop(meterRegistry.timer("logpulse.elasticsearch.indexing.time",
                    "type", "batch"));

        } catch (Exception e) {
            indexingErrorsCounter.increment();
            log.error("Failed to save logs to Elasticsearch in bulk: {}", e.getMessage());
            elasticsearchAvailable.set(false);

            // 실패한 로그는 큐에 추가
            indexingQueue.addAll(logEntries);
            queueSizeGauge.set(indexingQueue.size());

            batchIndexingTimer.stop(meterRegistry.timer("logpulse.elasticsearch.indexing.time",
                    "type", "batch",
                    "result", "error"));
        }
    }

    @Async("elasticsearchExecutor")
    public void saveAllAsync(List<LogEntry> logEntries) {
        if (logEntries == null || logEntries.isEmpty()) {
            return;
        }

        // 비동기 인덱싱을 위해 큐에 추가
        indexingQueue.addAll(logEntries);
        int newSize = queueSizeGauge.addAndGet(logEntries.size());

        log.debug("Added {} logs to async indexing queue. Queue size: {}", logEntries.size(), newSize);

        // 현재 비동기 인덱싱이 진행 중이 아니면 처리 시작
        if (asyncIndexingInProgress.compareAndSet(false, true)) {
            processIndexingQueue();
        }
    }

    private void processIndexingQueue() {
        if (!isAvailable()) {
            log.debug("Elasticsearch not available. Will retry indexing queue later. Queue size: {}", indexingQueue.size());
            asyncIndexingInProgress.set(false);
            return;
        }

        Timer.Sample queueProcessingTimer = Timer.start(meterRegistry);
        long startTime = System.currentTimeMillis();
        int processedCount = 0;

        try {
            List<LogEntry> batch = new ArrayList<>(asyncIndexingBatchSize);

            // 큐에서 배치 크기만큼 항목 추출
            while (!indexingQueue.isEmpty() && batch.size() < asyncIndexingBatchSize) {
                LogEntry entry = indexingQueue.poll();
                if (entry != null) {
                    batch.add(entry);
                }
            }

            if (!batch.isEmpty()) {
                saveAll(batch);
                processedCount = batch.size();

                // 큐 크기 업데이트
                queueSizeGauge.set(indexingQueue.size());

                log.debug("Processed {} logs from indexing queue. Remaining: {}",
                        processedCount, indexingQueue.size());

                // 큐에 더 많은 항목이 있으면 스로틀링 후 다시 처리
                if (!indexingQueue.isEmpty()) {
                    try {
                        Thread.sleep(indexingThrottleMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    // 큐 처리 재시작 (재귀 호출)
                    processIndexingQueue();
                    return;
                }
            }

            // 모든 항목을 처리했거나 큐가 비어 있으면 완료
            asyncIndexingInProgress.set(false);

            long endTime = System.currentTimeMillis();
            long elapsedTime = endTime - startTime;

            // 처리 성능 측정
            if (processedCount > 0 && elapsedTime > 0) {
                double docsPerSecond = processedCount * 1000.0 / elapsedTime;
                meterRegistry.gauge("logpulse.elasticsearch.indexing.rate", docsPerSecond);
            }

            queueProcessingTimer.stop(meterRegistry.timer("logpulse.elasticsearch.queue.processing.time"));

        } catch (Exception e) {
            log.error("Error processing indexing queue: {}", e.getMessage(), e);

            // 오류가 발생해도 처리 플래그는 해제
            asyncIndexingInProgress.set(false);

            // 오류 카운터 증가
            meterRegistry.counter("logpulse.elasticsearch.errors",
                    "operation", "queue_processing").increment();

            queueProcessingTimer.stop(meterRegistry.timer("logpulse.elasticsearch.queue.processing.time",
                    "result", "error"));
        }
    }

    @Scheduled(fixedRate = 30000)
    public void scheduleQueueProcessing() {
        if (!indexingQueue.isEmpty() && !asyncIndexingInProgress.get()) {
            log.debug("Scheduled processing of indexing queue. Size: {}", indexingQueue.size());
            if (asyncIndexingInProgress.compareAndSet(false, true)) {
                processIndexingQueue();
            }
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
            log.debug("Elasticsearch is not available. Returning empty result for complex search");
            return Collections.emptyList();
        }

        Timer.Sample searchTimer = Timer.start(meterRegistry);
        searchRequestsCounter.increment();

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

            // 결과 변환
            List<LogDocument> results = searchHits.getSearchHits().stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());

            // 검색 타이머 및 메트릭 기록
            searchTimer.stop(meterRegistry.timer("logpulse.elasticsearch.search.time",
                    "hits", results.isEmpty() ? "empty" : "found"));

            // 히트 수 메트릭 기록
            meterRegistry.counter("logpulse.elasticsearch.search.hits").increment(results.size());

            return results;

        } catch (Exception e) {
            log.error("Error performing complex search in Elasticsearch: {}", e.getMessage());
            elasticsearchAvailable.set(false);
            searchErrorsCounter.increment();

            searchTimer.stop(meterRegistry.timer("logpulse.elasticsearch.search.time",
                    "result", "error"));

            return Collections.emptyList();
        }
    }

    @Scheduled(fixedRate = 60000)
    public void logElasticsearchStats() {
        if (!elasticsearchEnabled) {
            return;
        }

        double docsIndexed = documentsIndexedCounter.count();
        double indexingErrors = indexingErrorsCounter.count();
        double searchRequests = searchRequestsCounter.count();
        double searchErrors = searchErrorsCounter.count();
        int queueSize = queueSizeGauge.get();

        // 인덱싱 및 검색 성공률 계산
        double indexingSuccessRate = docsIndexed > 0 ?
                (1 - (indexingErrors / docsIndexed)) * 100 : 100;
        double searchSuccessRate = searchRequests > 0 ?
                (1 - (searchErrors / searchRequests)) * 100 : 100;

        log.info("Elasticsearch stats - Indexed: {}, Queue: {}, Success rate: {:.2f}%, " +
                        "Search requests: {}, Search success rate: {:.2f}%",
                (long)docsIndexed,
                queueSize,
                indexingSuccessRate,
                (long)searchRequests,
                searchSuccessRate);

        // 성공률 게이지 업데이트
        meterRegistry.gauge("logpulse.elasticsearch.indexing.success.rate", indexingSuccessRate);
        meterRegistry.gauge("logpulse.elasticsearch.search.success.rate", searchSuccessRate);
    }
}