package com.haagendazs.common.event.payment;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WorkspaceCreatedPayload(
        @JsonProperty("workspace_id") Long workspaceId
) {
}
