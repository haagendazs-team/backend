package com.haagendazs.member.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "workspace_member")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "role", nullable = false)
    private String role;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static WorkspaceMember assign(Long workspaceId, Long memberId, WorkspaceRole role) {
        WorkspaceMember wm = new WorkspaceMember();
        wm.workspaceId = workspaceId;
        wm.memberId = memberId;
        wm.role = role.getValue();
        return wm;
    }

    public WorkspaceRole getWorkspaceRole() {
        return WorkspaceRole.valueOf(this.role);
    }
}
