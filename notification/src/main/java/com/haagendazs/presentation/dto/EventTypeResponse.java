package com.haagendazs.presentation.dto;

import com.haagendazs.domain.model.EventTypeDefinition;

public record EventTypeResponse(
        String code,
        boolean scheduled,
        boolean singleTarget,
        boolean enabled
) {
    public static EventTypeResponse of(EventTypeDefinition definition) {
        return new EventTypeResponse(
                definition.getCode(),
                definition.isScheduled(),
                definition.isSingleTarget(),
                definition.isEnabled()
        );
    }
}
