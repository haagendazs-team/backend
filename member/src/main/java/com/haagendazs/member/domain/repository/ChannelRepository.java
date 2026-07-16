package com.haagendazs.member.domain.repository;

import com.haagendazs.member.domain.model.Channel;

import java.util.List;
import java.util.Optional;

public interface ChannelRepository {

    Channel save(Channel channel);

    Optional<Channel> findById(Long channelId);

    List<Channel> findAllByWorkspaceIdAndChannelIdIn(Long workspaceId, List<Long> channelIds);

    Optional<Channel> findDmChannelByWorkspaceIdAndMemberIds(Long workspaceId, Long memberId1, Long memberId2);

    void delete(Channel channel);
}
