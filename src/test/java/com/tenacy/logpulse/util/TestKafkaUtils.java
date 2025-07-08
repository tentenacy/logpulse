package com.tenacy.logpulse.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.RecordsToDelete;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class TestKafkaUtils {

    private final KafkaAdmin kafkaAdmin;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 특정 토픽의 모든 메시지를 삭제하고 오프셋을 리셋 (안전한 방식)
     */
    public void cleanupTopic(String topicName) {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            log.info("Cleaning up topic: {}", topicName);

            try {
                // 1. 토픽 존재 여부 확인
                var topicMetadata = adminClient.describeTopics(Collections.singletonList(topicName))
                        .allTopicNames().get(5, TimeUnit.SECONDS);

                if (!topicMetadata.containsKey(topicName)) {
                    log.info("Topic {} does not exist, skipping cleanup", topicName);
                    return;
                }

                var partitions = topicMetadata.get(topicName).partitions();

                // 2. 각 파티션의 현재 오프셋 확인
                Map<TopicPartition, org.apache.kafka.clients.admin.OffsetSpec> requestLatestOffsets = new java.util.HashMap<>();
                for (var partition : partitions) {
                    TopicPartition tp = new TopicPartition(topicName, partition.partition());
                    requestLatestOffsets.put(tp, org.apache.kafka.clients.admin.OffsetSpec.latest());
                }

                var latestOffsets = adminClient.listOffsets(requestLatestOffsets)
                        .all().get(5, TimeUnit.SECONDS);

                // 3. 삭제할 레코드가 있는 파티션만 처리
                Map<TopicPartition, RecordsToDelete> recordsToDelete = new java.util.HashMap<>();
                for (Map.Entry<TopicPartition, org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo> entry : latestOffsets.entrySet()) {
                    long latestOffset = entry.getValue().offset();
                    if (latestOffset > 0) {
                        recordsToDelete.put(entry.getKey(), RecordsToDelete.beforeOffset(latestOffset));
                    }
                }

                // 4. 레코드 삭제 실행
                if (!recordsToDelete.isEmpty()) {
                    adminClient.deleteRecords(recordsToDelete).all().get(10, TimeUnit.SECONDS);
                    log.info("Successfully cleaned up {} partitions in topic: {}",
                            recordsToDelete.size(), topicName);
                } else {
                    log.info("No records to delete in topic: {}", topicName);
                }

            } catch (Exception e) {
                log.warn("Could not cleanup topic {}: {}", topicName, e.getMessage());
                // 토픽 정리 실패해도 테스트 계속 진행
            }

        } catch (Exception e) {
            log.warn("Failed to cleanup topic: {}, error: {}", topicName, e.getMessage());
            // 테스트 실행을 방해하지 않도록 예외를 던지지 않음
        }
    }

    /**
     * 컨슈머 그룹의 모든 오프셋을 0으로 리셋 (안전한 방식)
     */
    public void resetConsumerGroupOffsets(String groupId, String topicName) {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            log.info("Resetting consumer group offsets: group={}, topic={}", groupId, topicName);

            try {
                // 1. 컨슈머 그룹이 존재하는지 확인
                var describeResult = adminClient.describeConsumerGroups(Collections.singletonList(groupId));
                var groupDescriptions = describeResult.all().get(5, TimeUnit.SECONDS);

                if (groupDescriptions.isEmpty() || !groupDescriptions.containsKey(groupId)) {
                    log.info("Consumer group {} does not exist, skipping offset reset", groupId);
                    return;
                }

                // 2. 컨슈머 그룹 상태 확인
                var groupDescription = groupDescriptions.get(groupId);
                var groupState = groupDescription.state();
                log.debug("Consumer group {} state: {}", groupId, groupState);

                // 3. 현재 오프셋 조회 시도
                Map<TopicPartition, OffsetAndMetadata> currentOffsets = null;
                try {
                    ListConsumerGroupOffsetsResult offsetsResult = adminClient.listConsumerGroupOffsets(groupId);
                    currentOffsets = offsetsResult.partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);
                } catch (Exception offsetException) {
                    log.debug("Could not get current offsets: {}", offsetException.getMessage());
                    // 오프셋이 없으면 리셋할 필요 없음
                    return;
                }

                if (currentOffsets == null || currentOffsets.isEmpty()) {
                    log.info("No offsets to reset for group: {}", groupId);
                    return;
                }

                // 4. 해당 토픽의 오프셋만 필터링
                Map<TopicPartition, OffsetAndMetadata> resetOffsets = new java.util.HashMap<>();
                for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : currentOffsets.entrySet()) {
                    if (entry.getKey().topic().equals(topicName)) {
                        resetOffsets.put(entry.getKey(), new OffsetAndMetadata(0));
                    }
                }

                if (resetOffsets.isEmpty()) {
                    log.info("No offsets to reset for topic {} in group {}", topicName, groupId);
                    return;
                }

                // 5. 오프셋 리셋 시도
                try {
                    adminClient.alterConsumerGroupOffsets(groupId, resetOffsets)
                            .all().get(5, TimeUnit.SECONDS);
                    log.info("Successfully reset consumer group offsets for group: {}", groupId);
                } catch (Exception alterException) {
                    // UnknownMemberIdException이나 다른 오프셋 변경 오류는 경고만 출력
                    log.warn("Failed to alter offsets (this is often normal): {}", alterException.getMessage());
                }

            } catch (Exception e) {
                // 그룹이 존재하지 않거나 접근할 수 없는 경우
                log.warn("Could not access consumer group {}: {}", groupId, e.getMessage());
            }

        } catch (Exception e) {
            log.warn("Failed to reset consumer group offsets: group={}, topic={}, error={}",
                    groupId, topicName, e.getMessage());
        }
    }

    /**
     * 특정 토픽의 메시지 처리 완료까지 대기
     */
    public void waitForTopicProcessingComplete(String topicName, String consumerGroupId,
                                               Duration timeout) {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            log.info("Waiting for topic processing complete: topic={}, group={}",
                    topicName, consumerGroupId);

            long startTime = System.currentTimeMillis();
            long timeoutMs = timeout.toMillis();

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    // 토픽의 high water mark와 컨슈머 그룹의 현재 오프셋 비교
                    var topicMetadata = adminClient.describeTopics(Collections.singletonList(topicName))
                            .allTopicNames().get(5, TimeUnit.SECONDS);

                    var offsetsResult = adminClient.listConsumerGroupOffsets(consumerGroupId);
                    Map<TopicPartition, OffsetAndMetadata> consumerOffsets =
                            offsetsResult.partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);

                    boolean allProcessed = true;
                    for (var partition : topicMetadata.get(topicName).partitions()) {
                        TopicPartition tp = new TopicPartition(topicName, partition.partition());

                        // 현재 컨슈머 오프셋
                        OffsetAndMetadata consumerOffset = consumerOffsets.get(tp);
                        if (consumerOffset == null) {
                            allProcessed = false;
                            break;
                        }

                        // 토픽의 마지막 오프셋 (high water mark) 확인
                        var endOffsets = adminClient.listOffsets(
                                        Collections.singletonMap(tp,
                                                org.apache.kafka.clients.admin.OffsetSpec.latest()))
                                .all().get(5, TimeUnit.SECONDS);

                        long highWaterMark = endOffsets.get(tp).offset();

                        if (consumerOffset.offset() < highWaterMark) {
                            allProcessed = false;
                            break;
                        }
                    }

                    if (allProcessed) {
                        log.info("All messages processed for topic: {}", topicName);
                        return;
                    }

                    Thread.sleep(100); // 100ms 대기 후 재확인

                } catch (Exception e) {
                    log.debug("Error while checking processing status: {}", e.getMessage());
                    Thread.sleep(100);
                }
            }

            log.warn("Timeout waiting for topic processing complete: {}", topicName);

        } catch (Exception e) {
            log.error("Failed to wait for topic processing complete", e);
            throw new RuntimeException("Wait for processing failed", e);
        }
    }

    /**
     * 카프카 상태를 완전히 정리 (테스트용) - 안전한 방식
     */
    public void fullCleanup(String topicName, String consumerGroupId) {
        log.info("Starting safe Kafka cleanup for topic: {}, group: {}", topicName, consumerGroupId);

        try {
            // 1. 컨슈머 그룹 오프셋 리셋 (실패해도 계속 진행)
            resetConsumerGroupOffsets(consumerGroupId, topicName);

            // 2. 토픽 정리 (실패해도 계속 진행)
            cleanupTopic(topicName);

            // 3. 시스템 안정화 대기
            Thread.sleep(1000);

            log.info("Safe Kafka cleanup completed for topic: {}", topicName);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Cleanup interrupted for topic: {}", topicName);
        } catch (Exception e) {
            log.warn("Some cleanup operations failed for topic: {}, but continuing: {}",
                    topicName, e.getMessage());
            // 테스트 실행을 방해하지 않도록 예외를 던지지 않음
        }
    }
}