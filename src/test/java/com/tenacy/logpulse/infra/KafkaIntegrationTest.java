package com.tenacy.logpulse.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenacy.logpulse.api.dto.LogEventDto;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public class KafkaIntegrationTest {

    private static final String TEST_TOPIC = "test-raw-logs";

    @Container
    static KafkaContainer kafkaContainer = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private Consumer<String, String> consumer;

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
        registry.add("logpulse.kafka.topics.raw-logs", () -> TEST_TOPIC);
    }

    @BeforeEach
    void setUp() {
        // 테스트용 Kafka 컨슈머 설정
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        DefaultKafkaConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new StringDeserializer());

        consumer = cf.createConsumer();
        consumer.subscribe(Collections.singleton(TEST_TOPIC));
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    void canSendAndReceiveKafkaMessage() throws JsonProcessingException {
        // given
        LogEventDto logEventDto = LogEventDto.builder()
                .source("kafka-test")
                .content("Kafka 테스트 로그 내용")
                .logLevel("INFO")
                .timestamp(LocalDateTime.now())
                .build();

        String logEventJson = objectMapper.writeValueAsString(logEventDto);

        // when
        kafkaTemplate.send(TEST_TOPIC, logEventDto.getSource(), logEventJson);

        // then
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

        assertThat(records.count()).isGreaterThan(0);

        // 첫 번째 레코드 검증
        var record = records.iterator().next();
        assertThat(record.key()).isEqualTo("kafka-test");
        assertThat(record.value()).contains("kafka-test");
        assertThat(record.value()).contains("Kafka 테스트 로그 내용");
        assertThat(record.value()).contains("INFO");
    }
}