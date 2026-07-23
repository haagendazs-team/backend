package com.haagendazs.member.application.dto;

import com.haagendazs.member.domain.model.Member;

import java.time.LocalDateTime;

public record MemberResult(
        Long memberId,
        String email,
        String nickname,
        String profileImageUrl,
        LocalDateTime createdAt
) {
    public static MemberResult from(Member member) {
        return new MemberResult(
                member.getMemberId(),
                member.getEmail(),
                member.getNickname(),
                member.getProfileImageUrl(),
                member.getCreatedAt()
        );
    }
}
