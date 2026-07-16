package com.haagendazs.member.infrastructure.persistence;

import com.haagendazs.member.domain.model.WorkspaceMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceMemberJpaRepository extends JpaRepository<WorkspaceMember, Long> {

    List<WorkspaceMember> findAllByMemberId(Long memberId);

    List<WorkspaceMember> findAllByWorkspaceId(Long workspaceId);

    Optional<WorkspaceMember> findByWorkspaceIdAndMemberId(Long workspaceId, Long memberId);

    void deleteByWorkspaceIdAndMemberId(Long workspaceId, Long memberId);
}
