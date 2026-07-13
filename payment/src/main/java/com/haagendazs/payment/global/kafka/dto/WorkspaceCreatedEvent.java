package com.haagendazs.payment.global.kafka.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record WorkspaceCreatedEvent(
        @JsonAlias("workspaceId")
        @JsonProperty("workspace_id")
        Long workspaceId
) {
}
