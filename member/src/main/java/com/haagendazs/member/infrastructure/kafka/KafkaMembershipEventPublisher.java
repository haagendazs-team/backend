package com.haagendazs.member.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.common.event.search.MemberChannelJoinedPayload;
import com.haagendazs.common.event.search.MemberChannelLeftPayload;
import com.haagendazs.common.event.search.MemberWorkspaceJoinedPayload;
import com.haagendazs.common.event.search.MemberWorkspaceLeftPayload;
import com.haagendazs.common.event.search.SearchEventTopics;
import com.haagendazs.common.exception.InfrastructureErrorCode;
import com.haagendazs.common.exception.InfrastructureException;
import com.haagendazs.member.application.port.MembershipEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaMembershipEventPublisher implements MembershipEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publishWorkspaceJoined(Long memberId, Long workspaceId) {
        publish(
                SearchEventTopics.WORKSPACE_JOINED,
                new MemberWorkspaceJoinedPayload(memberId, workspaceId, Instant.now()),
                memberId
        );
    }

    @Override
    public void publishWorkspaceLeft(Long memberId, Long workspaceId) {
        publish(
                SearchEventTopics.WORKSPACE_LEFT,
                new MemberWorkspaceLeftPayload(memberId, workspaceId, Instant.now()),
                memberId
        );
    }

    @Override
    public void publishChannelJoined(Long memberId, Long channelId, Long workspaceId) {
        publish(
                SearchEventTopics.CHANNEL_JOINED,
                new MemberChannelJoinedPayload(memberId, channelId, workspaceId, Instant.now()),
                memberId
        );
    }

    @Override
    public void publishChannelLeft(Long memberId, Long channelId) {
        publish(
                SearchEventTopics.CHANNEL_LEFT,
                new MemberChannelLeftPayload(memberId, channelId, Instant.now()),
                memberId
        );
    }

    private void publish(String topic, Object payload, Long memberId) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            String key = String.valueOf(memberId);
            kafkaTemplate.send(topic, key, json);
            log.info("Kafka 이벤트 발행 topic={} memberId={}", topic, memberId);
        } catch (JsonProcessingException e) {
            throw new InfrastructureException(InfrastructureErrorCode.KAFKA_PUBLISH_FAILED, e);
        }
    }
}
