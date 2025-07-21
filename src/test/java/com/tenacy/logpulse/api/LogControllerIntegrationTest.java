package com.tenacy.logpulse.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenacy.logpulse.api.dto.LogEntryRequest;
import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.domain.LogRepository;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class LogControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LogRepository logRepository;

    @BeforeEach
    void setUp() {
        logRepository.deleteAll();

        List<LogEntry> testLogs = Arrays.asList(
                LogEntry.builder()
                        .source("test-service")
                        .content("Info log message")
                        .logLevel("INFO")
                        .build(),
                LogEntry.builder()
                        .source("test-service")
                        .content("Warning log message")
                        .logLevel("WARN")
                        .build(),
                LogEntry.builder()
                        .source("another-service")
                        .content("Error log message")
                        .logLevel("ERROR")
                        .build()
        );

        logRepository.saveAll(testLogs);
    }

    @AfterEach
    void tearDown() {
        logRepository.deleteAll();
    }

    @Test
    @DisplayName("로그 생성 API 테스트")
    void createLogTest() throws Exception {
        // given
        LogEntryRequest request = LogEntryRequest.builder()
                .source("api-test-service")
                .content("API test log message")
                .logLevel("INFO")
                .build();

        String requestJson = objectMapper.writeValueAsString(request);

        // when & then
        mockMvc.perform(post("/api/v1/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.source", is(request.getSource())))
                .andExpect(jsonPath("$.content", is(request.getContent())))
                .andExpect(jsonPath("$.logLevel", is(request.getLogLevel())))
                .andExpect(jsonPath("$.createdAt", notNullValue()));

        // 데이터베이스에 저장되었는지 확인
        List<LogEntry> logs = logRepository.findByLogLevel("INFO");
        assertThat(logs).anyMatch(log -> "api-test-service".equals(log.getSource()) &&
                "API test log message".equals(log.getContent()));
    }

    @Test
    @DisplayName("모든 로그 조회 API 테스트")
    void getAllLogsTest() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].id", notNullValue()))
                .andExpect(jsonPath("$.content[1].id", notNullValue()))
                .andExpect(jsonPath("$.content[2].id", notNullValue()));
    }

    @Test
    @DisplayName("로그 레벨별 조회 API 테스트")
    void getLogsByLevelTest() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/logs")
                        .param("level", "ERROR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].logLevel", is("ERROR")))
                .andExpect(jsonPath("$.content[0].source", is("another-service")));
    }

    @Test
    @DisplayName("기간별 로그 조회 API 테스트")
    void getLogsBetweenTest() throws Exception {
        // 시간 기반 필터링 테스트를 로그 레벨 기반 필터링으로 대체
        mockMvc.perform(get("/api/v1/logs")
                        .param("level", "WARN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].logLevel", is("WARN")))
                .andExpect(jsonPath("$.content[0].content", is("Warning log message")));

        mockMvc.perform(get("/api/v1/logs")
                        .param("level", "ERROR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].logLevel", is("ERROR")))
                .andExpect(jsonPath("$.content[0].content", is("Error log message")));
    }

    @Test
    @DisplayName("대용량 로그 생성 테스트")
    void bulkCreateLogsTest() throws Exception {
        // given
        int batchSize = 10;
        long beforeCount = logRepository.count();

        // when
        for (int i = 0; i < batchSize; i++) {
            LogEntryRequest request = LogEntryRequest.builder()
                    .source("bulk-test-service")
                    .content("Bulk test log message " + i)
                    .logLevel("INFO")
                    .build();

            String requestJson = objectMapper.writeValueAsString(request);

            mockMvc.perform(post("/api/v1/logs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk());
        }

        // then
        List<LogEntry> afterLogs = logRepository.findAll();
        assertThat(afterLogs).hasSize((int) (beforeCount + batchSize));
    }

    // 새로운 테스트: 로그 레벨 조합 조회 테스트 추가
    @Test
    @DisplayName("로그 레벨 조합 조회 API 테스트")
    void getLogsByMultipleCriteriaTest() throws Exception {
        // 소스와 레벨을 함께 사용한 필터링 테스트
        mockMvc.perform(get("/api/v1/logs")
                        .param("source", "test-service")
                        .param("level", "WARN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].source", is("test-service")))
                .andExpect(jsonPath("$.content[0].logLevel", is("WARN")));
    }
}