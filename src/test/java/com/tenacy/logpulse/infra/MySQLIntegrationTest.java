package com.tenacy.logpulse.infra;

import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.domain.LogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class MySQLIntegrationTest {

    @Container
    static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("logpulse_test")
            .withUsername("root")
            .withPassword("1234");

    @Autowired
    private LogRepository logRepository;

    @DynamicPropertySource
    static void registerMySQLProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
    }

    @Test
    void canSaveAndRetrieveLogEntry() {
        // given
        LogEntry logEntry = LogEntry.builder()
                .source("test-source")
                .content("테스트 로그 내용")
                .logLevel("INFO")
                .createdAt(LocalDateTime.now())
                .compressed(false)
                .build();

        // when
        LogEntry savedEntry = logRepository.save(logEntry);
        LogEntry retrievedEntry = logRepository.findById(savedEntry.getId()).orElse(null);

        // then
        assertThat(retrievedEntry).isNotNull();
        assertThat(retrievedEntry.getSource()).isEqualTo("test-source");
        assertThat(retrievedEntry.getContent()).isEqualTo("테스트 로그 내용");
        assertThat(retrievedEntry.getLogLevel()).isEqualTo("INFO");
    }

    @Test
    void canCountLogsByLevel() {
        // given
        LocalDateTime now = LocalDateTime.now();

        // 여러 로그 항목 저장
        LogEntry errorLog = LogEntry.builder()
                .source("test-source")
                .content("에러 로그")
                .logLevel("ERROR")
                .createdAt(now)
                .compressed(false)
                .build();

        LogEntry infoLog = LogEntry.builder()
                .source("test-source")
                .content("정보 로그")
                .logLevel("INFO")
                .createdAt(now)
                .compressed(false)
                .build();

        logRepository.save(errorLog);
        logRepository.save(infoLog);

        // when
        long errorCount = logRepository.countByLogLevelAndCreatedAtBetween(
                "ERROR", now.minusMinutes(1), now.plusMinutes(1));
        long infoCount = logRepository.countByLogLevelAndCreatedAtBetween(
                "INFO", now.minusMinutes(1), now.plusMinutes(1));

        // then
        assertThat(errorCount).isEqualTo(1);
        assertThat(infoCount).isEqualTo(1);
    }
}