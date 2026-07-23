package com.haagendazs.member.application.dto;

import com.haagendazs.member.domain.model.Channel;

import java.time.LocalDateTime;

public record ChannelResult(
        Long channelId,
        Long workspaceId,
        String name,
        boolean isDirectMessage,
        LocalDateTime createdAt
) {
    public static ChannelResult from(Channel channel) {
        return new ChannelResult(
                channel.getChannelId(),
                channel.getWorkspaceId(),
                channel.getName(),
                channel.isDirectMessage(),
                channel.getCreatedAt()
        );
    }
}
