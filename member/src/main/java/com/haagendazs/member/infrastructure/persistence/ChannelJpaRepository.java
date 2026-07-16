package com.haagendazs.member.infrastructure.persistence;

import com.haagendazs.member.domain.model.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChannelJpaRepository extends JpaRepository<Channel, Long> {

    List<Channel> findAllByWorkspaceId(Long workspaceId);

    List<Channel> findAllByWorkspaceIdAndChannelIdIn(Long workspaceId, List<Long> channelIds);

    @Query("""
            SELECT c FROM Channel c
            WHERE c.workspaceId = :workspaceId
            AND c.isDirectMessage = true
            AND c.channelId IN (
                SELECT cm1.channelId FROM ChannelMember cm1 WHERE cm1.memberId = :memberId1
            )
            AND c.channelId IN (
                SELECT cm2.channelId FROM ChannelMember cm2 WHERE cm2.memberId = :memberId2
            )
            """)
    Optional<Channel> findDmChannelByWorkspaceIdAndMemberIds(
            @Param("workspaceId") Long workspaceId,
            @Param("memberId1") Long memberId1,
            @Param("memberId2") Long memberId2
    );
}
