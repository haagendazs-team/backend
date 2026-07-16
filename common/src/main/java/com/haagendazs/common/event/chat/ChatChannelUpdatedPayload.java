package com.haagendazs.common.event.chat;

public record ChatChannelUpdatedPayload(
        Long channelId,
        String channelName
) {
}
