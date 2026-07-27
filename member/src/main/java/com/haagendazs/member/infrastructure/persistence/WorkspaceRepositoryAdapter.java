package com.haagendazs.member.infrastructure.persistence;

import com.haagendazs.member.domain.model.Workspace;
import com.haagendazs.member.domain.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class WorkspaceRepositoryAdapter implements WorkspaceRepository {

    private final WorkspaceJpaRepository jpaRepository;

    @Override
    public Workspace save(Workspace workspace) {
        return jpaRepository.save(workspace);
    }

    @Override
    public Optional<Workspace> findById(Long workspaceId) {
        return jpaRepository.findById(workspaceId);
    }

    @Override
    public List<Workspace> findAllByIdIn(Collection<Long> workspaceIds) {
        if (workspaceIds == null || workspaceIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findAllById(workspaceIds);
    }

    @Override
    public void delete(Workspace workspace) {
        jpaRepository.delete(workspace);
    }
}
