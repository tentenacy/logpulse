package com.tenacy.logpulse.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.domain.LogRepository;
import com.tenacy.logpulse.util.SimpleTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class LogIntegrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LogRepository logRepository;

    @Autowired
    private SimpleTestSupport testSupport;

    @Value("${logpulse.kafka.topics.raw-logs}")
    private String rawLogsTopic;

    @Value("${spring.kafka.consumer.group-id}")
    private String consumerGroupId;

    @BeforeEach
    void setUp() {
        logRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        logRepository.deleteAll();
    }

    @Test
    @DisplayName("통합 로그 생성 API 테스트")
    void createIntegrationLogTest() throws Exception {
        // given
        LogEventDto logEventDto = LogEventDto.builder()
                .source("integration-test-service")
                .content("Integration API test log message")
                .logLevel("INFO")
                .build();

        String requestJson = objectMapper.writeValueAsString(logEventDto);

        // when
        mockMvc.perform(post("/api/v1/logs/integration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")));

        // 비동기 처리 완료까지 대기 (최대 10초)
        testSupport.waitForProcessingComplete(1, Duration.ofSeconds(10));

        // then
        List<LogEntry> logs = logRepository.findBySourceContaining("integration-test-service");
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getContent()).isEqualTo("Integration API test log message");
        assertThat(logs.get(0).getLogLevel()).isEqualTo("INFO");
    }

    @Test
    @DisplayName("통합 로그 생성 API 배치 테스트")
    void createBatchIntegrationLogsTest() throws Exception {
        // given
        int batchSize = 10;
        int initialCount = (int) logRepository.count();

        // when
        for (int i = 0; i < batchSize; i++) {
            LogEventDto logEventDto = LogEventDto.builder()
                    .source("integration-batch-test")
                    .content("Batch integration test log message " + i)
                    .logLevel("INFO")
                    .build();

            String requestJson = objectMapper.writeValueAsString(logEventDto);

            mockMvc.perform(post("/api/v1/logs/integration")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk());
        }

        // 비동기 처리 완료까지 대기 (최대 30초)
        testSupport.waitForProcessingComplete(initialCount + batchSize, Duration.ofSeconds(30));

        // then
        List<LogEntry> logs = logRepository.findBySourceContaining("integration-batch-test");
        assertThat(logs).hasSize(batchSize);
    }
}