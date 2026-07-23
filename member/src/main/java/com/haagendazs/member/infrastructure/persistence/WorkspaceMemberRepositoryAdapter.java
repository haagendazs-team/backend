package com.haagendazs.member.infrastructure.persistence;

import com.haagendazs.member.domain.model.WorkspaceMember;
import com.haagendazs.member.domain.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class WorkspaceMemberRepositoryAdapter implements WorkspaceMemberRepository {

    private final WorkspaceMemberJpaRepository jpaRepository;

    @Override
    public WorkspaceMember save(WorkspaceMember workspaceMember) {
        return jpaRepository.save(workspaceMember);
    }

    @Override
    public List<WorkspaceMember> findAllByMemberId(Long memberId) {
        return jpaRepository.findAllByMemberId(memberId);
    }

    @Override
    public List<WorkspaceMember> findAllByWorkspaceId(Long workspaceId) {
        return jpaRepository.findAllByWorkspaceId(workspaceId);
    }

    @Override
    public Optional<WorkspaceMember> findByWorkspaceIdAndMemberId(Long workspaceId, Long memberId) {
        return jpaRepository.findByWorkspaceIdAndMemberId(workspaceId, memberId);
    }

    @Override
    public void deleteByWorkspaceIdAndMemberId(Long workspaceId, Long memberId) {
        jpaRepository.deleteByWorkspaceIdAndMemberId(workspaceId, memberId);
    }
}
