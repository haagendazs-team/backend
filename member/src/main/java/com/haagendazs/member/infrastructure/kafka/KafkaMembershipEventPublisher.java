package com.haagendazs.member.infrastructure.kafka;

import com.haagendazs.common.event.search.MemberChannelJoinedPayload;
import com.haagendazs.common.event.search.MemberChannelLeftPayload;
import com.haagendazs.common.event.search.MemberWorkspaceJoinedPayload;
import com.haagendazs.common.event.search.MemberWorkspaceLeftPayload;
import com.haagendazs.common.event.search.SearchEventTopics;
import com.haagendazs.member.application.port.MembershipEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaMembershipEventPublisher implements MembershipEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishWorkspaceJoined(Long memberId, Long workspaceId) {
        send(
                SearchEventTopics.WORKSPACE_JOINED,
                memberId.toString(),
                new MemberWorkspaceJoinedPayload(memberId, workspaceId, Instant.now())
        );
    }

    @Override
    public void publishWorkspaceLeft(Long memberId, Long workspaceId) {
        send(
                SearchEventTopics.WORKSPACE_LEFT,
                memberId.toString(),
                new MemberWorkspaceLeftPayload(memberId, workspaceId, Instant.now())
        );
    }

    @Override
    public void publishChannelJoined(Long memberId, Long channelId, Long workspaceId) {
        send(
                SearchEventTopics.CHANNEL_JOINED,
                memberId.toString(),
                new MemberChannelJoinedPayload(memberId, channelId, workspaceId, Instant.now())
        );
    }

    @Override
    public void publishChannelLeft(Long memberId, Long channelId) {
        send(
                SearchEventTopics.CHANNEL_LEFT,
                memberId.toString(),
                new MemberChannelLeftPayload(memberId, channelId, Instant.now())
        );
    }

    private void send(String topic, String key, Object event) {
        kafkaTemplate.send(topic, key, event);
        log.info("Kafka 이벤트 발행 topic={} key={}", topic, key);
    }
}
