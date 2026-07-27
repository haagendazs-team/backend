package com.haagendazs.member.domain.repository;

import com.haagendazs.member.domain.model.Workspace;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkspaceRepository {

    Workspace save(Workspace workspace);

    Optional<Workspace> findById(Long workspaceId);

    List<Workspace> findAllByIdIn(Collection<Long> workspaceIds);

    void delete(Workspace workspace);
}
