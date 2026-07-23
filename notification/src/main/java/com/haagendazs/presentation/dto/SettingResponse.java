package com.haagendazs.presentation.dto;

import com.haagendazs.application.dto.SettingResult;

public record SettingResponse(
        String eventTypeCode,
        String displayName,
        String category,
        boolean enabled
) {
    public static SettingResponse from(SettingResult result) {
        return new SettingResponse(
                result.eventTypeCode(),
                result.displayName(),
                result.category(),
                result.enabled()
        );
    }
}
