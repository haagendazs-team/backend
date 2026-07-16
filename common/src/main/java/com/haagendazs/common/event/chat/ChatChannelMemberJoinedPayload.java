package com.haagendazs.common.event.chat;

public record ChatChannelMemberJoinedPayload(
        Long channelId,
        Long memberId
) {
}
