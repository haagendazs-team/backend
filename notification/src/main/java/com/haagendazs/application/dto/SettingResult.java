package com.haagendazs.application.dto;

import com.haagendazs.domain.model.EventTypeDefinition;
import com.haagendazs.domain.model.SettingEntry;

public record SettingResult(
        Long memberId,
        String eventTypeCode,
        boolean enabled,
        String displayName,
        String category
) {
    public static SettingResult from(SettingEntry entry, EventTypeDefinition def) {
        return new SettingResult(
                entry.getMemberId(),
                entry.getEventTypeCode(),
                entry.isEnabled(),
                def.getDisplayName(),
                def.getCategory()
        );
    }

    public static SettingResult defaultEnabled(Long memberId, EventTypeDefinition def) {
        return new SettingResult(memberId, def.getCode(), true, def.getDisplayName(), def.getCategory());
    }
}
