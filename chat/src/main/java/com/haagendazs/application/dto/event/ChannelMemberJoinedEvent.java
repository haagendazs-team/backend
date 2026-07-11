package com.haagendazs.application.dto.event;

public record ChannelMemberJoinedEvent(
   Long channelId,
   Long memberId
) {}
