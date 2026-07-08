package com.haagendazs.member.application.dto;

import com.haagendazs.member.domain.model.WorkspaceMember;

import java.time.LocalDateTime;

public record WorkspaceMemberResult(
        Long memberId,
        String role,
        LocalDateTime joinedAt
) {
    public static WorkspaceMemberResult from(WorkspaceMember workspaceMember) {
        return new WorkspaceMemberResult(
                workspaceMember.getMemberId(),
                workspaceMember.getRole(),
                workspaceMember.getCreatedAt()
        );
    }
}
