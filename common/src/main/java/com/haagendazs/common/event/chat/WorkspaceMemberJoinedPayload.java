package com.haagendazs.common.event.chat;

public record WorkspaceMemberJoinedPayload(
        Long memberId,
        String nickname,
        String profileImageUrl,
        Long workspaceId
) {
}
