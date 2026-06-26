package com.haagendazs.application.dto;

import com.haagendazs.domain.model.Channel;
import com.haagendazs.domain.model.ChannelType;

public record ChannelResult(
        Long id,
        ChannelType channelType,
        String channelTarget,
        boolean enabled
) {
    public static ChannelResult from(Channel channel) {
        return new ChannelResult(
                channel.getId(),
                channel.getChannelType(),
                channel.getChannelTarget(),
                channel.isEnabled()
        );
    }
}
