package com.haagendazs.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterEventTypeRequest(
        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,48}")
        String code,

        @NotBlank
        @Pattern(regexp = "^notif:stream:[a-z][a-z0-9.]{1,88}$")
        String streamKey,

        @NotNull Boolean scheduled,
        @NotNull Boolean singleTarget,

        @NotBlank String memberIdField,

        String scheduledAtField,

        Integer scheduledOffsetMinutes
) {
    public int resolvedOffsetMinutes() {
        return scheduledOffsetMinutes != null ? scheduledOffsetMinutes : 0;
    }
}
