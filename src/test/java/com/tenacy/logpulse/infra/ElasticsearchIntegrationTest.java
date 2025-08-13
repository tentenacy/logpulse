package com.tenacy.logpulse.infra;

import com.tenacy.logpulse.elasticsearch.document.LogDocument;
import com.tenacy.logpulse.elasticsearch.repository.LogDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public class ElasticsearchIntegrationTest {

    @Container
    static ElasticsearchContainer elasticsearchContainer = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:7.17.0")
    ).withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false");

    @Autowired
    private LogDocumentRepository logDocumentRepository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @DynamicPropertySource
    static void registerElasticsearchProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", () ->
                "http://" + elasticsearchContainer.getHost() + ":" + elasticsearchContainer.getMappedPort(9200));
    }

    @Test
    void canSaveAndRetrieveLogDocument() {
        // given
        String id = UUID.randomUUID().toString();
        LogDocument logDocument = LogDocument.builder()
                .id(id)
                .source("elasticsearch-test")
                .content("Elasticsearch 테스트 로그 내용")
                .logLevel("INFO")
                .timestamp(LocalDateTime.now())
                .build();

        // when
        LogDocument savedDocument = logDocumentRepository.save(logDocument);

        // 인덱스 갱신을 위한 대기
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // then
        LogDocument retrievedDocument = logDocumentRepository.findById(id).orElse(null);

        assertThat(retrievedDocument).isNotNull();
        assertThat(retrievedDocument.getSource()).isEqualTo("elasticsearch-test");
        assertThat(retrievedDocument.getContent()).isEqualTo("Elasticsearch 테스트 로그 내용");
        assertThat(retrievedDocument.getLogLevel()).isEqualTo("INFO");
    }

    @Test
    void canSearchLogDocuments() {
        // given
        LogDocument doc1 = LogDocument.builder()
                .id(UUID.randomUUID().toString())
                .source("search-test")
                .content("테스트 검색 로그 내용")
                .logLevel("INFO")
                .timestamp(LocalDateTime.now())
                .build();

        LogDocument doc2 = LogDocument.builder()
                .id(UUID.randomUUID().toString())
                .source("search-test")
                .content("다른 검색 로그 내용")
                .logLevel("ERROR")
                .timestamp(LocalDateTime.now())
                .build();

        logDocumentRepository.save(doc1);
        logDocumentRepository.save(doc2);

        // 인덱스 갱신을 위한 대기
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // then
        elasticsearchOperations.indexOps(LogDocument.class).refresh();

        // when
        List<LogDocument> errorLogs = logDocumentRepository.findByLogLevel("ERROR");
        List<LogDocument> searchResults = logDocumentRepository.findByContentContaining("검색");

        // then
        assertThat(errorLogs).isNotEmpty();
        assertThat(errorLogs.stream().map(LogDocument::getLogLevel))
                .contains("ERROR");

        assertThat(searchResults).hasSizeGreaterThanOrEqualTo(1);
        assertThat(searchResults.stream().map(LogDocument::getContent))
                .anyMatch(content -> content.contains("검색"));
    }
}