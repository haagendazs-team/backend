package com.haagendazs.application.dto.event;

public record ChannelMemberDeletedEvent(
        Long channelId,
        Long memberId
) {}
