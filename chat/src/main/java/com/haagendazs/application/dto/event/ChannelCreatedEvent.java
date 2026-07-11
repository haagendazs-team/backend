package com.haagendazs.application.dto.event;

public record ChannelCreatedEvent(
        Long channelId,
        Long workspaceId,
        String channelName,
        boolean isDirectMessage
)
{}
