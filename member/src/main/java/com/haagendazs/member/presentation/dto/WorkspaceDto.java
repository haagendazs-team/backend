package com.haagendazs.member.presentation.dto;

import com.haagendazs.member.application.dto.WorkspaceMemberResult;
import com.haagendazs.member.application.dto.WorkspaceResult;

import java.time.LocalDateTime;

public class WorkspaceDto {

    public record CreateWorkspaceRequest(
            String name,
            String iconUrl
    ) {
    }

    public record UpdateWorkspaceRequest(
            String name,
            String iconUrl
    ) {
    }

    public record InviteMemberRequest(
            String email,
            String role
    ) {
    }

    public record WorkspaceResponse(
            Long workspaceId,
            String name,
            String iconUrl,
            String subscription,
            LocalDateTime createdAt
    ) {
        public static WorkspaceResponse from(WorkspaceResult result) {
            return new WorkspaceResponse(
                    result.workspaceId(),
                    result.name(),
                    result.iconUrl(),
                    result.subscription(),
                    result.createdAt()
            );
        }
    }

    public record WorkspaceMemberResponse(
            Long memberId,
            String role,
            LocalDateTime joinedAt
    ) {
        public static WorkspaceMemberResponse from(WorkspaceMemberResult result) {
            return new WorkspaceMemberResponse(
                    result.memberId(),
                    result.role(),
                    result.joinedAt()
            );
        }
    }
}
