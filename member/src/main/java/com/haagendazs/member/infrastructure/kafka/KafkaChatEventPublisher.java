package com.haagendazs.member.infrastructure.kafka;

import com.haagendazs.common.event.chat.ChatChannelCreatedPayload;
import com.haagendazs.common.event.chat.ChatChannelDeletedPayload;
import com.haagendazs.common.event.chat.ChatChannelMemberDeletedPayload;
import com.haagendazs.common.event.chat.ChatChannelMemberJoinedPayload;
import com.haagendazs.common.event.chat.ChatChannelUpdatedPayload;
import com.haagendazs.common.event.chat.ChatEventTopics;
import com.haagendazs.common.event.chat.ChatMemberDeletedPayload;
import com.haagendazs.common.event.chat.ChatMemberUpdatedPayload;
import com.haagendazs.common.event.chat.WorkspaceMemberJoinedPayload;
import com.haagendazs.member.application.port.ChatEventPublisher;
import com.haagendazs.member.domain.model.Channel;
import com.haagendazs.member.domain.model.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaChatEventPublisher implements ChatEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishWorkspaceMemberJoined(Member member, Long workspaceId) {
        send(
                ChatEventTopics.WORKSPACE_MEMBER_JOINED,
                member.getMemberId().toString(),
                new WorkspaceMemberJoinedPayload(
                        member.getMemberId(),
                        member.getNickname(),
                        member.getProfileImageUrl(),
                        workspaceId
                )
        );
    }

    @Override
    public void publishMemberUpdated(Member member) {
        send(
                ChatEventTopics.MEMBER_UPDATED,
                member.getMemberId().toString(),
                new ChatMemberUpdatedPayload(
                        member.getMemberId(),
                        member.getNickname(),
                        member.getProfileImageUrl()
                )
        );
    }

    @Override
    public void publishMemberDeleted(Member member) {
        send(
                ChatEventTopics.MEMBER_DELETED,
                member.getMemberId().toString(),
                new ChatMemberDeletedPayload(member.getMemberId())
        );
    }

    @Override
    public void publishChannelCreated(Channel channel) {
        send(
                ChatEventTopics.CHANNEL_CREATED,
                channel.getChannelId().toString(),
                new ChatChannelCreatedPayload(
                        channel.getChannelId(),
                        channel.getWorkspaceId(),
                        channel.isDirectMessage() ? null : channel.getName(),
                        channel.isDirectMessage()
                )
        );
    }

    @Override
    public void publishChannelUpdated(Channel channel) {
        send(
                ChatEventTopics.CHANNEL_UPDATED,
                channel.getChannelId().toString(),
                new ChatChannelUpdatedPayload(channel.getChannelId(), channel.getName())
        );
    }

    @Override
    public void publishChannelDeleted(Long channelId) {
        send(
                ChatEventTopics.CHANNEL_DELETED,
                channelId.toString(),
                new ChatChannelDeletedPayload(channelId)
        );
    }

    @Override
    public void publishChannelMemberJoined(Long channelId, Long memberId) {
        send(
                ChatEventTopics.CHANNEL_MEMBER_JOINED,
                channelId.toString(),
                new ChatChannelMemberJoinedPayload(channelId, memberId)
        );
    }

    @Override
    public void publishChannelMemberDeleted(Long channelId, Long memberId) {
        send(
                ChatEventTopics.CHANNEL_MEMBER_DELETED,
                channelId.toString(),
                new ChatChannelMemberDeletedPayload(channelId, memberId)
        );
    }

    private void send(String topic, String key, Object event) {
        kafkaTemplate.send(topic, key, event);
        log.info("Kafka 채팅 이벤트 발행 topic={} key={}", topic, key);
    }
}
