package com.haagendazs.common.event.chat;

public record ChatMemberUpdatedPayload(
        Long memberId,
        String nickname,
        String profileImageUrl
) {
}
