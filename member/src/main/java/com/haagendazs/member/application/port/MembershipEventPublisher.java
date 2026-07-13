package com.haagendazs.member.application.port;

public interface MembershipEventPublisher {

    void publishWorkspaceJoined(Long memberId, Long workspaceId);

    void publishWorkspaceLeft(Long memberId, Long workspaceId);

    void publishChannelJoined(Long memberId, Long channelId, Long workspaceId);

    void publishChannelLeft(Long memberId, Long channelId);
}
