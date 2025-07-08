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

        // 테스트 데이터 설정
        List<LogEntry> testLogs = Arrays.asList(
                LogEntry.builder()
                        .source("test-service")
                        .content("Info log message")
                        .logLevel("INFO")
                        .createdAt(LocalDateTime.now().minusHours(2))
                        .build(),
                LogEntry.builder()
                        .source("test-service")
                        .content("Warning log message")
                        .logLevel("WARN")
                        .createdAt(LocalDateTime.now().minusHours(1))
                        .build(),
                LogEntry.builder()
                        .source("another-service")
                        .content("Error log message")
                        .logLevel("ERROR")
                        .createdAt(LocalDateTime.now())
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
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id", notNullValue()))
                .andExpect(jsonPath("$[1].id", notNullValue()))
                .andExpect(jsonPath("$[2].id", notNullValue()));
    }

    @Test
    @DisplayName("로그 레벨별 조회 API 테스트")
    void getLogsByLevelTest() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/logs/level/ERROR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].logLevel", is("ERROR")))
                .andExpect(jsonPath("$[0].source", is("another-service")));
    }

    @Test
    @DisplayName("기간별 로그 조회 API 테스트")
    void getLogsBetweenTest() throws Exception {
        // 현재 시간 기준으로 충분한 범위
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusHours(1);  // 1시간 전
        LocalDateTime end = now.plusMinutes(5);   // 5분 후

        // 모든 로그 조회 (넓은 범위)
        mockMvc.perform(get("/api/v1/logs/period")
                        .param("start", start.toString())
                        .param("end", end.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        // 과거 시간으로 테스트 (아무것도 포함되지 않음)
        LocalDateTime pastStart = now.minusHours(2);
        LocalDateTime pastEnd = now.minusHours(1);
        mockMvc.perform(get("/api/v1/logs/period")
                        .param("start", pastStart.toString())
                        .param("end", pastEnd.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0))); // 0개 기대
    }

    @Test
    @DisplayName("대용량 로그 생성 테스트")
    void bulkCreateLogsTest() throws Exception {
        // given
        int batchSize = 100;
        List<LogEntry> beforeLogs = logRepository.findAll();
        int beforeCount = beforeLogs.size();

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
        assertThat(afterLogs).hasSize(beforeCount + batchSize);
    }
}