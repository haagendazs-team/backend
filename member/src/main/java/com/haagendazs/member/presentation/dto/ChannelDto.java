package com.haagendazs.member.presentation.dto;

import com.haagendazs.member.application.dto.ChannelMemberResult;
import com.haagendazs.member.application.dto.ChannelResult;

import java.time.LocalDateTime;

public class ChannelDto {

    public record CreateChannelRequest(
            String name
    ) {
    }

    public record UpdateChannelRequest(
            String name
    ) {
    }

    public record ChannelResponse(
            Long channelId,
            Long workspaceId,
            String name,
            boolean isDirectMessage,
            LocalDateTime createdAt
    ) {
        public static ChannelResponse from(ChannelResult result) {
            return new ChannelResponse(
                    result.channelId(),
                    result.workspaceId(),
                    result.name(),
                    result.isDirectMessage(),
                    result.createdAt()
            );
        }
    }

    public record ChannelMemberResponse(
            Long memberId,
            LocalDateTime joinedAt
    ) {
        public static ChannelMemberResponse from(ChannelMemberResult result) {
            return new ChannelMemberResponse(
                    result.memberId(),
                    result.joinedAt()
            );
        }
    }
}
