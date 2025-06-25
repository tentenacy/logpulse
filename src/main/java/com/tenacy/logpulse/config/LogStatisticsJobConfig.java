package com.tenacy.logpulse.config;

import com.tenacy.logpulse.batch.LogStatisticsProcessor;
import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.domain.LogStatistics;
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
public class LogStatisticsJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;

    @Value("${logpulse.batch.chunk-size:100}")
    private int chunkSize;

    @Bean
    public Job logStatisticsJob() {
        return new JobBuilder("logStatisticsJob", jobRepository)
                .start(generateDailyStatisticsStep())
                .build();
    }

    @Bean
    public Step generateDailyStatisticsStep() {
        return new StepBuilder("generateDailyStatisticsStep", jobRepository)
                .<LogEntry, LogStatistics>chunk(chunkSize, transactionManager)
                .reader(todayLogsReader())
                .processor(logStatisticsProcessor())
                .writer(statisticsWriter())
                .build();
    }

    @Bean
    @StepScope
    public JdbcCursorItemReader<LogEntry> todayLogsReader() {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        return new JdbcCursorItemReaderBuilder<LogEntry>()
                .name("todayLogsReader")
                .dataSource(dataSource)
                .sql("SELECT id, source, content, log_level, created_at FROM logs WHERE created_at BETWEEN ? AND ?")
                .preparedStatementSetter(ps -> {
                    ps.setObject(1, startOfDay);
                    ps.setObject(2, endOfDay);
                })
                .rowMapper(new DataClassRowMapper<>(LogEntry.class))
                .build();
    }

    @Bean
    @StepScope
    public LogStatisticsProcessor logStatisticsProcessor() {
        return new LogStatisticsProcessor();
    }

    @Bean
    @StepScope
    public JdbcBatchItemWriter<LogStatistics> statisticsWriter() {
        return new JdbcBatchItemWriterBuilder<LogStatistics>()
                .dataSource(dataSource)
                .sql("INSERT INTO log_statistics (log_date, source, log_level, count, created_at) " +
                        "VALUES (:logDate, :source, :logLevel, :count, NOW())")
                .beanMapped()
                .build();
    }
}