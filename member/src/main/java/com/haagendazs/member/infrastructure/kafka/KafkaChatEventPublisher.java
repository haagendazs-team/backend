package com.haagendazs.member.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.common.event.chat.ChatChannelCreatedPayload;
import com.haagendazs.common.event.chat.ChatChannelDeletedPayload;
import com.haagendazs.common.event.chat.ChatChannelMemberDeletedPayload;
import com.haagendazs.common.event.chat.ChatChannelMemberJoinedPayload;
import com.haagendazs.common.event.chat.ChatChannelUpdatedPayload;
import com.haagendazs.common.event.chat.ChatEventTopics;
import com.haagendazs.common.event.chat.ChatMemberDeletedPayload;
import com.haagendazs.common.event.chat.ChatMemberUpdatedPayload;
import com.haagendazs.common.event.chat.WorkspaceMemberJoinedPayload;
import com.haagendazs.common.exception.InfrastructureErrorCode;
import com.haagendazs.common.exception.InfrastructureException;
import com.haagendazs.member.application.port.ChatEventPublisher;
import com.haagendazs.member.domain.model.Channel;
import com.haagendazs.member.domain.model.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaChatEventPublisher implements ChatEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publishWorkspaceMemberJoined(Member member, Long workspaceId) {
        publish(
                ChatEventTopics.WORKSPACE_MEMBER_JOINED,
                new WorkspaceMemberJoinedPayload(
                        member.getMemberId(),
                        member.getNickname(),
                        member.getProfileImageUrl(),
                        workspaceId
                ),
                String.valueOf(member.getMemberId())
        );
    }

    @Override
    public void publishMemberUpdated(Member member) {
        publish(
                ChatEventTopics.MEMBER_UPDATED,
                new ChatMemberUpdatedPayload(
                        member.getMemberId(),
                        member.getNickname(),
                        member.getProfileImageUrl()
                ),
                String.valueOf(member.getMemberId())
        );
    }

    @Override
    public void publishMemberDeleted(Member member) {
        publish(
                ChatEventTopics.MEMBER_DELETED,
                new ChatMemberDeletedPayload(member.getMemberId()),
                String.valueOf(member.getMemberId())
        );
    }

    @Override
    public void publishChannelCreated(Channel channel) {
        publish(
                ChatEventTopics.CHANNEL_CREATED,
                new ChatChannelCreatedPayload(
                        channel.getChannelId(),
                        channel.getWorkspaceId(),
                        channel.isDirectMessage() ? null : channel.getName(),
                        channel.isDirectMessage()
                ),
                String.valueOf(channel.getChannelId())
        );
    }

    @Override
    public void publishChannelUpdated(Channel channel) {
        publish(
                ChatEventTopics.CHANNEL_UPDATED,
                new ChatChannelUpdatedPayload(channel.getChannelId(), channel.getName()),
                String.valueOf(channel.getChannelId())
        );
    }

    @Override
    public void publishChannelDeleted(Long channelId) {
        publish(
                ChatEventTopics.CHANNEL_DELETED,
                new ChatChannelDeletedPayload(channelId),
                String.valueOf(channelId)
        );
    }

    @Override
    public void publishChannelMemberJoined(Long channelId, Long memberId) {
        publish(
                ChatEventTopics.CHANNEL_MEMBER_JOINED,
                new ChatChannelMemberJoinedPayload(channelId, memberId),
                String.valueOf(channelId)
        );
    }

    @Override
    public void publishChannelMemberDeleted(Long channelId, Long memberId) {
        publish(
                ChatEventTopics.CHANNEL_MEMBER_DELETED,
                new ChatChannelMemberDeletedPayload(channelId, memberId),
                String.valueOf(channelId)
        );
    }

    private void publish(String topic, Object payload, String key) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, key, json);
            log.info("Kafka 채팅 이벤트 발행 topic={} key={}", topic, key);
        } catch (JsonProcessingException e) {
            throw new InfrastructureException(InfrastructureErrorCode.KAFKA_PUBLISH_FAILED, e);
        }
    }
}
