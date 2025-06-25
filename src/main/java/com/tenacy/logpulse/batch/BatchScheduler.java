package com.tenacy.logpulse.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class BatchScheduler {

    private final JobLauncher jobLauncher;

    @Qualifier("logArchiveJob")
    private final Job logArchiveJob;

    @Qualifier("logStatisticsJob")
    private final Job logStatisticsJob;

    // 매일 자정에 실행
    @Scheduled(cron = "0 0 0 * * ?")
    public void runDailyBatchJobs() {
        runArchiveJob();
        runStatisticsJob();
    }

    private void runArchiveJob() {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("time", LocalDateTime.now().toString())
                    .toJobParameters();

            jobLauncher.run(logArchiveJob, jobParameters);
            log.info("Log archive job completed successfully");
        } catch (Exception e) {
            log.error("Error running log archive job", e);
        }
    }

    private void runStatisticsJob() {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("time", LocalDateTime.now().toString())
                    .toJobParameters();

            jobLauncher.run(logStatisticsJob, jobParameters);
            log.info("Log statistics job completed successfully");
        } catch (Exception e) {
            log.error("Error running log statistics job", e);
        }
    }
}