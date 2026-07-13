package com.haagendazs.common.event.chat;

public record ChatChannelMemberDeletedPayload(
        Long channelId,
        Long memberId
) {
}
