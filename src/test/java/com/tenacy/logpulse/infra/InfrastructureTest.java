package com.tenacy.logpulse.infra;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public class InfrastructureTest {

    // MySQL 컨테이너 설정
    @Container
    static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("logpulse_test")
            .withUsername("root")
            .withPassword("1234");

    // Elasticsearch 컨테이너 설정
    @Container
    static ElasticsearchContainer elasticsearchContainer = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:7.17.0")
    ).withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false");

    // Kafka 컨테이너 설정
    @Container
    static KafkaContainer kafkaContainer = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );

    @BeforeAll
    static void setup() {
        // 컨테이너 시작
        mysqlContainer.start();
        elasticsearchContainer.start();
        kafkaContainer.start();
    }

    @AfterAll
    static void tearDown() {
        // 컨테이너 종료
        mysqlContainer.stop();
        elasticsearchContainer.stop();
        kafkaContainer.stop();
    }

    // 동적 프로퍼티 설정
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // MySQL 속성 설정
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);

        // Elasticsearch 속성 설정
        registry.add("spring.elasticsearch.uris", () -> "http://" + elasticsearchContainer.getHost() + ":" + elasticsearchContainer.getMappedPort(9200));

        // Kafka 속성 설정
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    }

    @Test
    void infrastructureIsRunning() {
        // 모든 컨테이너가 실행 중인지 확인
        assertTrue(mysqlContainer.isRunning(), "MySQL 컨테이너가 실행 중이어야 합니다");
        assertTrue(elasticsearchContainer.isRunning(), "Elasticsearch 컨테이너가 실행 중이어야 합니다");
        assertTrue(kafkaContainer.isRunning(), "Kafka 컨테이너가 실행 중이어야 합니다");
    }
}