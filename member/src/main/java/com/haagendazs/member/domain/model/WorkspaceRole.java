package com.haagendazs.member.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WorkspaceRole {

    OWNER("OWNER"),
    ADMIN("ADMIN"),
    MEMBER("MEMBER");

    private final String value;

    public boolean canManageWorkspace() {
        return this == OWNER || this == ADMIN;
    }
}
