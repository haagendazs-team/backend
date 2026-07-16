package com.haagendazs.member.infrastructure.persistence;

import com.haagendazs.member.domain.model.Channel;
import com.haagendazs.member.domain.repository.ChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChannelRepositoryAdapter implements ChannelRepository {

    private final ChannelJpaRepository jpaRepository;

    @Override
    public Channel save(Channel channel) {
        return jpaRepository.save(channel);
    }

    @Override
    public Optional<Channel> findById(Long channelId) {
        return jpaRepository.findById(channelId);
    }

    @Override
    public List<Channel> findAllByWorkspaceIdAndChannelIdIn(Long workspaceId, List<Long> channelIds) {
        if (channelIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findAllByWorkspaceIdAndChannelIdIn(workspaceId, channelIds);
    }

    @Override
    public Optional<Channel> findDmChannelByWorkspaceIdAndMemberIds(Long workspaceId, Long memberId1, Long memberId2) {
        return jpaRepository.findDmChannelByWorkspaceIdAndMemberIds(workspaceId, memberId1, memberId2);
    }

    @Override
    public void delete(Channel channel) {
        jpaRepository.delete(channel);
    }
}
