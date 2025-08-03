package com.tenacy.logpulse.config;

import com.tenacy.logpulse.batch.LogArchiveProcessor;
import com.tenacy.logpulse.domain.LogEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDateTime;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class LogArchiveJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;

    @Value("${logpulse.batch.chunk-size:100}")
    private int chunkSize;

    @Value("${logpulse.batch.archive-days:30}")
    private int archiveDays;

    @Bean
    public Job logArchiveJob() {
        return new JobBuilder("logArchiveJob", jobRepository)
                .start(archiveOldLogsStep())
                .build();
    }

    @Bean
    public Step archiveOldLogsStep() {
        return new StepBuilder("archiveOldLogsStep", jobRepository)
                .<LogEntry, LogEntry>chunk(chunkSize, transactionManager)
                .reader(oldLogsReader())
                .processor(logArchiveProcessor())
                .writer(archiveLogsWriter())
                .build();
    }

    @Bean
    @StepScope
    public JdbcCursorItemReader<LogEntry> oldLogsReader() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(archiveDays);

        return new JdbcCursorItemReaderBuilder<LogEntry>()
                .name("oldLogsReader")
                .dataSource(dataSource)
                .sql("SELECT id, source, content, log_level, created_at, compressed, original_size, compressed_size " +
                        "FROM logs WHERE created_at < ?")
                .preparedStatementSetter(ps -> ps.setObject(1, cutoffDate))
                .rowMapper(new DataClassRowMapper<>(LogEntry.class))
                .build();
    }

    @Bean
    @StepScope
    public LogArchiveProcessor logArchiveProcessor() {
        return new LogArchiveProcessor();
    }

    @Bean
    @StepScope
    public JdbcBatchItemWriter<LogEntry> archiveLogsWriter() {
        return new JdbcBatchItemWriterBuilder<LogEntry>()
                .dataSource(dataSource)
                .sql("INSERT INTO log_archives (id, source, content, log_level, created_at, archived_at, " +
                        "compressed, original_size, compressed_size) " +
                        "VALUES (:id, :source, :content, :logLevel, :createdAt, NOW(), " +
                        ":compressed, :originalSize, :compressedSize)")
                .beanMapped()
                .build();
    }
}