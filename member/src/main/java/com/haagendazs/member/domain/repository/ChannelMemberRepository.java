package com.haagendazs.member.domain.repository;

import com.haagendazs.member.domain.model.ChannelMember;

import java.util.List;

public interface ChannelMemberRepository {

    ChannelMember save(ChannelMember channelMember);

    List<ChannelMember> findAllByMemberId(Long memberId);

    List<ChannelMember> findAllByChannelId(Long channelId);

    boolean existsByChannelIdAndMemberId(Long channelId, Long memberId);

    void deleteByChannelIdAndMemberId(Long channelId, Long memberId);

    void deleteAllByChannelId(Long channelId);
}
