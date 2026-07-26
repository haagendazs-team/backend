package com.haagendazs.domain.repository;

import com.haagendazs.domain.model.ChatParticipant;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

    boolean existsByChannelIdAndMemberId(Long channelId, Long memberId);
    Optional<ChatParticipant> findByChannelIdAndMemberId(Long channelId, Long memberId);
    void deleteByChannelIdAndMemberId(Long channelId, Long memberId);
}
