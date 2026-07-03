package com.haagendazs.presentation.dto;

import com.haagendazs.domain.model.PingStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record PingAckRequest(
        @NotNull PingStatus status,
        @NotNull LocalDateTime pingReceivedAt
) {}
