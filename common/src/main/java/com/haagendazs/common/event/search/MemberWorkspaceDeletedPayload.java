package com.haagendazs.common.event.search;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record MemberWorkspaceDeletedPayload(
        @JsonProperty("workspace_id") Long workspaceId,
        @JsonProperty("deleted_at") Instant deletedAt
) {
}
