package com.haagendazs.member.domain.repository;

import com.haagendazs.member.domain.model.Workspace;

import java.util.Optional;

public interface WorkspaceRepository {

    Workspace save(Workspace workspace);

    Optional<Workspace> findById(Long workspaceId);

    void delete(Workspace workspace);
}
