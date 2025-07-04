package com.tenacy.logpulse.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

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

    @Bean
    public CommonErrorHandler kafkaCommonErrorHandler() {
        return new DefaultErrorHandler(
                new FixedBackOff(1000L, 3)  // 3번 재시도, 1초 간격
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            CommonErrorHandler kafkaCommonErrorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaCommonErrorHandler);
        return factory;
    }
}