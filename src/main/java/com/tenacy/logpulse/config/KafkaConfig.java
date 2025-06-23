package com.tenacy.logpulse.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${logpulse.kafka.topics.raw-logs}")
    private String rawLogsTopic;

    @Bean
    public NewTopic rawLogsTopic() {
        return TopicBuilder.name(rawLogsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}