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
            log.info("로그 아카이브 작업이 성공적으로 완료되었습니다");
        } catch (Exception e) {
            log.error("로그 아카이브 작업 실행 중 오류 발생", e);
        }
    }

    private void runStatisticsJob() {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("time", LocalDateTime.now().toString())
                    .toJobParameters();

            jobLauncher.run(logStatisticsJob, jobParameters);
            log.info("로그 통계 작업이 성공적으로 완료되었습니다");
        } catch (Exception e) {
            log.error("로그 통계 작업 실행 중 오류 발생", e);
        }
    }
}