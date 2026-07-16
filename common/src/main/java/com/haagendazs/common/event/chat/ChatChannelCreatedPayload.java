package com.haagendazs.common.event.chat;

public record ChatChannelCreatedPayload(
        Long channelId,
        Long workspaceId,
        String channelName,
        boolean isDirectMessage
) {
}
