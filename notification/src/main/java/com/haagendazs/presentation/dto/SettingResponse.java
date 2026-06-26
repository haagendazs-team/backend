package com.haagendazs.presentation.dto;

import com.haagendazs.application.dto.SettingResult;

public record SettingResponse(
        String eventTypeCode,
        boolean enabled
) {
    public static SettingResponse from(SettingResult result) {
        return new SettingResponse(result.eventTypeCode(), result.enabled());
    }
}
