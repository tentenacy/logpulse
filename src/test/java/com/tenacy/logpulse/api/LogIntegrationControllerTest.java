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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    private SimpleTestSupport simpleTestSupport;

    @BeforeEach
    void setUp() {
        simpleTestSupport.basicCleanup();
    }

    @AfterEach
    void tearDown() {
        simpleTestSupport.simpleTestIsolation();
    }

    @Test
    @DisplayName("통합 로그 생성 API 테스트")
    void createIntegrationLogTest() throws Exception {
        // given
        LogEventDto logEventDto = LogEventDto.builder()
                .source("integration-test-service")
                .content("Integration API test log message")
                .logLevel("INFO")
                .timestamp(LocalDateTime.now())
                .build();

        String requestJson = objectMapper.writeValueAsString(logEventDto);

        // when
        mockMvc.perform(post("/api/v1/logs/integration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")));

        // 비동기 처리 완료까지 대기 (최대 30초)
        simpleTestSupport.waitForProcessingComplete(1, Duration.ofSeconds(30));

        // then
        List<LogEntry> logs = logRepository.findBySourceContaining("integration-test-service");
        assertThat(logs).isNotEmpty(); // 적어도 하나 이상의 로그가 있어야 함

        if (!logs.isEmpty()) {
            LogEntry log = logs.get(0);
            assertThat(log.getSource()).isEqualTo("integration-test-service");
            assertThat(log.getContent()).isEqualTo("Integration API test log message");
            assertThat(log.getLogLevel()).isEqualTo("INFO");
        }
    }

    @Test
    @DisplayName("통합 로그 생성 API 배치 테스트")
    void createBatchIntegrationLogsTest() throws Exception {
        // given
        int batchSize = 5; // 테스트에서는 수량 줄임

        // when
        for (int i = 0; i < batchSize; i++) {
            LogEventDto logEventDto = LogEventDto.builder()
                    .source("integration-batch-test")
                    .content("Batch integration test log message " + i)
                    .logLevel("INFO")
                    .timestamp(LocalDateTime.now())
                    .build();

            String requestJson = objectMapper.writeValueAsString(logEventDto);

            mockMvc.perform(post("/api/v1/logs/integration")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk());

            // 각 요청 사이에 약간의 대기 시간 추가
            Thread.sleep(100);
        }

        // 비동기 처리 완료까지 대기 (최대 60초)
        // 최소 1개 이상의 로그가 처리되기를 기대
        simpleTestSupport.waitForProcessingComplete(1, Duration.ofSeconds(60));

        // then
        List<LogEntry> logs = logRepository.findBySourceContaining("integration-batch-test");
        int processedCount = logs.size();

        System.out.println("Batch test processed logs: " + processedCount + " out of " + batchSize);
        logs.forEach(log -> System.out.println("  - " + log.getContent()));

        // 적어도 하나 이상의 로그가 처리되었는지 확인
        assertThat(logs).isNotEmpty();
    }
}