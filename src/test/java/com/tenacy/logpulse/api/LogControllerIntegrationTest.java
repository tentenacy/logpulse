package com.tenacy.logpulse.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenacy.logpulse.api.dto.LogEntryRequest;
import com.tenacy.logpulse.domain.LogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM logs");

        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update(
                "INSERT INTO logs (source, content, log_level, created_at, compressed) VALUES (?, ?, ?, ?, ?)",
                "test-service", "정보 로그 메시지", "INFO", now.minusMinutes(15), false);

        jdbcTemplate.update(
                "INSERT INTO logs (source, content, log_level, created_at, compressed) VALUES (?, ?, ?, ?, ?)",
                "test-service", "경고 로그 메시지", "WARN", now.minusMinutes(10), false);

        jdbcTemplate.update(
                "INSERT INTO logs (source, content, log_level, created_at, compressed) VALUES (?, ?, ?, ?, ?)",
                "api-service", "에러 로그 메시지", "ERROR", now.minusMinutes(5), false);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM logs");
    }

    @Test
    @DisplayName("로그 생성 API - 정상 요청 시 로그 생성 확인")
    void createLog_ShouldReturnCreatedLog() throws Exception {
        // given
        LogEntryRequest request = LogEntryRequest.builder()
                .source("api-test")
                .content("API 테스트 로그 메시지")
                .logLevel("INFO")
                .build();

        String requestJson = objectMapper.writeValueAsString(request);

        // when & then
        mockMvc.perform(post("/api/v1/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source", is(request.getSource())))
                .andExpect(jsonPath("$.content", is(request.getContent())))
                .andExpect(jsonPath("$.logLevel", is(request.getLogLevel())))
                .andExpect(jsonPath("$.createdAt", notNullValue()));
    }

    @Test
    @DisplayName("로그 조회 API - 레벨 필터링 확인")
    void getLogs_FilterByLevel_ShouldReturnFilteredLogs() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/logs")
                        .param("level", "ERROR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].logLevel", is("ERROR")));

        mockMvc.perform(get("/api/v1/logs")
                        .param("level", "INFO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].logLevel", is("INFO")));
    }

    @Test
    @DisplayName("로그 조회 API - 소스 필터링 확인")
    void getLogs_FilterBySource_ShouldReturnFilteredLogs() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/logs")
                        .param("source", "api-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].source", is("api-service")));

        mockMvc.perform(get("/api/v1/logs")
                        .param("source", "test-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    @DisplayName("로그 조회 API - 페이지네이션 확인")
    void getLogs_WithPagination_ShouldReturnPagedResult() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/logs")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.totalPages", is(2)));

        mockMvc.perform(get("/api/v1/logs")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("로그 조회 API - 정렬 확인")
    void getLogs_WithSorting_ShouldReturnSortedResult() throws Exception {
        // when & then - 기본 정렬은 createdAt 내림차순
        mockMvc.perform(get("/api/v1/logs")
                        .param("sortDir", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].logLevel", is("ERROR")))  // 가장 최근 로그
                .andExpect(jsonPath("$.content[2].logLevel", is("INFO"))); // 가장 오래된 로그

        // 오름차순으로 정렬
        mockMvc.perform(get("/api/v1/logs")
                        .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].logLevel", is("INFO")))  // 가장 오래된 로그
                .andExpect(jsonPath("$.content[2].logLevel", is("ERROR")));  // 가장 최근 로그
    }
}