package com.haagendazs.member.infrastructure.kafka;

import com.haagendazs.common.event.search.MemberChannelCreatedPayload;
import com.haagendazs.common.event.search.MemberChannelDeletedPayload;
import com.haagendazs.common.event.search.MemberChannelRenamedPayload;
import com.haagendazs.common.event.search.MemberWorkspaceDeletedPayload;
import com.haagendazs.common.event.search.SearchEventTopics;
import com.haagendazs.member.application.port.SearchIndexEventPublisher;
import com.haagendazs.member.domain.model.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaSearchIndexEventPublisher implements SearchIndexEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishChannelCreated(Channel channel) {
        Instant createdAt = channel.getCreatedAt() != null
                ? channel.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
                : Instant.now();
        send(
                SearchEventTopics.CHANNEL_CREATED,
                channel.getChannelId().toString(),
                new MemberChannelCreatedPayload(
                        channel.getChannelId(),
                        channel.getWorkspaceId(),
                        channel.getName(),
                        channel.isDirectMessage(),
                        createdAt
                )
        );
    }

    @Override
    public void publishChannelRenamed(Channel channel) {
        send(
                SearchEventTopics.CHANNEL_RENAMED,
                channel.getChannelId().toString(),
                new MemberChannelRenamedPayload(channel.getChannelId(), channel.getName(), Instant.now())
        );
    }

    @Override
    public void publishChannelDeleted(Long channelId) {
        send(
                SearchEventTopics.CHANNEL_DELETED,
                channelId.toString(),
                new MemberChannelDeletedPayload(channelId, Instant.now())
        );
    }

    @Override
    public void publishWorkspaceDeleted(Long workspaceId) {
        send(
                SearchEventTopics.WORKSPACE_DELETED,
                workspaceId.toString(),
                new MemberWorkspaceDeletedPayload(workspaceId, Instant.now())
        );
    }

    private void send(String topic, String key, Object event) {
        kafkaTemplate.send(topic, key, event);
        log.info("Kafka 이벤트 발행 topic={} key={}", topic, key);
    }
}
