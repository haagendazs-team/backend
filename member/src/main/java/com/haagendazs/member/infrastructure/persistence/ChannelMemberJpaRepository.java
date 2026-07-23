package com.haagendazs.member.infrastructure.persistence;

import com.haagendazs.member.domain.model.ChannelMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChannelMemberJpaRepository extends JpaRepository<ChannelMember, Long> {

    List<ChannelMember> findAllByMemberId(Long memberId);

    List<ChannelMember> findAllByChannelId(Long channelId);

    boolean existsByChannelIdAndMemberId(Long channelId, Long memberId);

    void deleteByChannelIdAndMemberId(Long channelId, Long memberId);

    void deleteAllByChannelId(Long channelId);
}
