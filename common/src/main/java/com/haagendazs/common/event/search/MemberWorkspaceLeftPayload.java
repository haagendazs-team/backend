package com.haagendazs.common.event.search;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record MemberWorkspaceLeftPayload(
        @JsonProperty("member_id") Long memberId,
        @JsonProperty("workspace_id") Long workspaceId,
        @JsonProperty("left_at") Instant leftAt
) {
}
