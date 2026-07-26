package com.haagendazs.application.dto.event;

public record WorkspaceMemberJoinedEvent(
    Long memberId,
    String nickname,
    String profileImageUrl,
    Long workspaceId
    ){}
