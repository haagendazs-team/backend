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

    /** 초대 API로 부여 가능한 역할 (OWNER는 생성자 전용, 이전은 별도 API) */
    public boolean isAssignableOnInvite() {
        return this == ADMIN || this == MEMBER;
    }
}
