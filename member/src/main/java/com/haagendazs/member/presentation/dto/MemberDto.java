package com.haagendazs.member.presentation.dto;

import com.haagendazs.member.application.dto.MemberResult;

import java.time.LocalDateTime;

public class MemberDto {

    public record UpdateProfileRequest(
            String nickname,
            String profileImageUrl
    ) {
    }

    public record MemberResponse(
            Long memberId,
            String email,
            String nickname,
            String profileImageUrl,
            LocalDateTime createdAt
    ) {
        public static MemberResponse from(MemberResult result) {
            return new MemberResponse(
                    result.memberId(),
                    result.email(),
                    result.nickname(),
                    result.profileImageUrl(),
                    result.createdAt()
            );
        }
    }
}
