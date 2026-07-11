package com.haagendazs.application.dto.event;

public record ChannelUpdatedEvent(
        Long channelId,
        String channelName
) {}
