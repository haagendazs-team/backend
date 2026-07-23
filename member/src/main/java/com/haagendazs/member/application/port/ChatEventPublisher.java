package com.haagendazs.member.application.port;

import com.haagendazs.member.domain.model.Channel;
import com.haagendazs.member.domain.model.Member;

public interface ChatEventPublisher {

    void publishWorkspaceMemberJoined(Member member, Long workspaceId);

    void publishMemberUpdated(Member member);

    void publishMemberDeleted(Member member);

    void publishChannelCreated(Channel channel);

    void publishChannelUpdated(Channel channel);

    void publishChannelDeleted(Long channelId);

    void publishChannelMemberJoined(Long channelId, Long memberId);

    void publishChannelMemberDeleted(Long channelId, Long memberId);
}
