package com.haagendazs.member.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.common.event.search.MemberChannelCreatedPayload;
import com.haagendazs.common.event.search.MemberChannelDeletedPayload;
import com.haagendazs.common.event.search.MemberChannelRenamedPayload;
import com.haagendazs.common.event.search.MemberWorkspaceDeletedPayload;
import com.haagendazs.common.event.search.SearchEventTopics;
import com.haagendazs.common.exception.InfrastructureErrorCode;
import com.haagendazs.common.exception.InfrastructureException;
import com.haagendazs.member.application.port.SearchIndexEventPublisher;
import com.haagendazs.member.domain.model.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaSearchIndexEventPublisher implements SearchIndexEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publishChannelCreated(Channel channel) {
        Instant createdAt = channel.getCreatedAt() != null
                ? channel.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
                : Instant.now();
        publish(
                SearchEventTopics.CHANNEL_CREATED,
                new MemberChannelCreatedPayload(
                        channel.getChannelId(),
                        channel.getWorkspaceId(),
                        channel.getName(),
                        channel.isDirectMessage(),
                        createdAt
                ),
                String.valueOf(channel.getChannelId())
        );
    }

    @Override
    public void publishChannelRenamed(Channel channel) {
        publish(
                SearchEventTopics.CHANNEL_RENAMED,
                new MemberChannelRenamedPayload(channel.getChannelId(), channel.getName(), Instant.now()),
                String.valueOf(channel.getChannelId())
        );
    }

    @Override
    public void publishChannelDeleted(Long channelId) {
        publish(
                SearchEventTopics.CHANNEL_DELETED,
                new MemberChannelDeletedPayload(channelId, Instant.now()),
                String.valueOf(channelId)
        );
    }

    @Override
    public void publishWorkspaceDeleted(Long workspaceId) {
        publish(
                SearchEventTopics.WORKSPACE_DELETED,
                new MemberWorkspaceDeletedPayload(workspaceId, Instant.now()),
                String.valueOf(workspaceId)
        );
    }

    private void publish(String topic, Object payload, String key) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, key, json);
            log.info("Kafka 이벤트 발행 topic={} key={}", topic, key);
        } catch (JsonProcessingException e) {
            throw new InfrastructureException(InfrastructureErrorCode.KAFKA_PUBLISH_FAILED, e);
        }
    }
}
