package com.haagendazs.application.dto.event;

public record MemberUpdatedEvent(
        Long memberId,
        String nickname,
        String profileImageUrl
) {}
