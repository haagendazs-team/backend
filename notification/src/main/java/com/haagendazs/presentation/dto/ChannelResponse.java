package com.haagendazs.presentation.dto;

import com.haagendazs.application.dto.ChannelResult;
import com.haagendazs.domain.model.ChannelType;

public record ChannelResponse(
        Long id,
        ChannelType channelType,
        String channelTarget,
        boolean enabled
) {
    public static ChannelResponse from(ChannelResult result) {
        return new ChannelResponse(
                result.id(),
                result.channelType(),
                result.channelTarget(),
                result.enabled()
        );
    }
}
