package com.haagendazs.member.application.port;

import com.haagendazs.member.domain.model.Channel;

public interface SearchIndexEventPublisher {

    void publishChannelCreated(Channel channel);

    void publishChannelRenamed(Channel channel);

    void publishChannelDeleted(Long channelId);

    void publishWorkspaceDeleted(Long workspaceId);
}
