package com.haagendazs.member.application.dto;

import com.haagendazs.member.domain.model.ChannelMember;

import java.time.LocalDateTime;

public record ChannelMemberResult(
        Long memberId,
        LocalDateTime joinedAt
) {
    public static ChannelMemberResult from(ChannelMember channelMember) {
        return new ChannelMemberResult(
                channelMember.getMemberId(),
                channelMember.getCreatedAt()
        );
    }
}
