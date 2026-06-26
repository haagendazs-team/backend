package com.haagendazs.presentation.dto;

import com.haagendazs.domain.model.EventTypeDefinition;

public record EventTypeResponse(
        String code,
        String streamKey,
        boolean scheduled,
        boolean singleTarget,
        String memberIdField,
        String scheduledAtField,
        int scheduledOffsetMinutes,
        boolean enabled
) {
    public static EventTypeResponse of(EventTypeDefinition definition) {
        return new EventTypeResponse(
                definition.getCode(),
                definition.getStreamKey(),
                definition.isScheduled(),
                definition.isSingleTarget(),
                definition.getMemberIdField(),
                definition.getScheduledAtField(),
                definition.getScheduledOffsetMinutes(),
                definition.isEnabled()
        );
    }
}
