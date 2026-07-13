package com.haagendazs.member.domain.repository;

import com.haagendazs.member.domain.model.WorkspaceMember;

import java.util.List;
import java.util.Optional;

public interface WorkspaceMemberRepository {

    WorkspaceMember save(WorkspaceMember workspaceMember);

    List<WorkspaceMember> findAllByMemberId(Long memberId);

    List<WorkspaceMember> findAllByWorkspaceId(Long workspaceId);

    Optional<WorkspaceMember> findByWorkspaceIdAndMemberId(Long workspaceId, Long memberId);

    void deleteByWorkspaceIdAndMemberId(Long workspaceId, Long memberId);
}
