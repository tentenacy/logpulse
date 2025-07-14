package com.tenacy.logpulse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "emailTaskExecutor")
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);  // 기본 스레드 풀 크기
        executor.setMaxPoolSize(5);   // 최대 스레드 풀 크기
        executor.setQueueCapacity(100);  // 대기 큐 용량
        executor.setThreadNamePrefix("email-");  // 스레드 이름 접두사
        executor.initialize();
        return executor;
    }
}