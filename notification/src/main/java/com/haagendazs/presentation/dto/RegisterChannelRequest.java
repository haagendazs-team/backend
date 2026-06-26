package com.haagendazs.presentation.dto;

import com.haagendazs.domain.model.ChannelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterChannelRequest(
        @NotNull ChannelType channelType,
        @NotBlank String channelTarget
) {}
