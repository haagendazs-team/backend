package com.haagendazs.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipantRepository, Long> {

    boolean existsByChannelIdAndMemberId(Long channelId, Long memberId);
}
