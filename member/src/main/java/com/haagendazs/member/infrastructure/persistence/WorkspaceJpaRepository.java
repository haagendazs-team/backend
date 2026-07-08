package com.haagendazs.member.infrastructure.persistence;

import com.haagendazs.member.domain.model.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceJpaRepository extends JpaRepository<Workspace, Long> {
}
