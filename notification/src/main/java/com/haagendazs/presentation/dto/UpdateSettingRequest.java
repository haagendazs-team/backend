package com.haagendazs.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateSettingRequest(
        @NotBlank String eventTypeCode,
        @NotNull Boolean enabled
) {}
