package com.haagendazs.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record K6BulkPublishRequest(
        @NotNull @Min(1) Long startMemberId,
        @NotNull @Min(1) @Max(1000) Integer count,
        @NotBlank String eventTypeCode,
        Long publishedAt
) {}
