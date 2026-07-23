package com.haagendazs.member.application.dto;

import com.haagendazs.member.domain.model.Workspace;

import java.time.LocalDateTime;

public record WorkspaceResult(
        Long workspaceId,
        String name,
        String iconUrl,
        String subscription,
        LocalDateTime createdAt
) {
    public static WorkspaceResult from(Workspace workspace) {
        return new WorkspaceResult(
                workspace.getWorkspaceId(),
                workspace.getName(),
                workspace.getIconUrl(),
                workspace.getSubscription(),
                workspace.getCreatedAt()
        );
    }
}
