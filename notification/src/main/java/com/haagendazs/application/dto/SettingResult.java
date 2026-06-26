package com.haagendazs.application.dto;

import com.haagendazs.domain.model.SettingEntry;

public record SettingResult(
        Long memberId,
        String eventTypeCode,
        boolean enabled
) {
    public static SettingResult from(SettingEntry entry) {
        return new SettingResult(entry.getMemberId(), entry.getEventTypeCode(), entry.isEnabled());
    }
}
