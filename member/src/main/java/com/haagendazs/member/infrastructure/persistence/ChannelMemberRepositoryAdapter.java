package com.haagendazs.member.infrastructure.persistence;

import com.haagendazs.member.domain.model.ChannelMember;
import com.haagendazs.member.domain.repository.ChannelMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ChannelMemberRepositoryAdapter implements ChannelMemberRepository {

    private final ChannelMemberJpaRepository jpaRepository;

    @Override
    public ChannelMember save(ChannelMember channelMember) {
        return jpaRepository.save(channelMember);
    }

    @Override
    public List<ChannelMember> findAllByMemberId(Long memberId) {
        return jpaRepository.findAllByMemberId(memberId);
    }

    @Override
    public List<ChannelMember> findAllByChannelId(Long channelId) {
        return jpaRepository.findAllByChannelId(channelId);
    }

    @Override
    public boolean existsByChannelIdAndMemberId(Long channelId, Long memberId) {
        return jpaRepository.existsByChannelIdAndMemberId(channelId, memberId);
    }

    @Override
    public void deleteByChannelIdAndMemberId(Long channelId, Long memberId) {
        jpaRepository.deleteByChannelIdAndMemberId(channelId, memberId);
    }

    @Override
    public void deleteAllByChannelId(Long channelId) {
        jpaRepository.deleteAllByChannelId(channelId);
    }
}
