package com.haagendazs.common.event.search;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record MemberWorkspaceJoinedPayload(
        @JsonProperty("member_id") Long memberId,
        @JsonProperty("workspace_id") Long workspaceId,
        @JsonProperty("joined_at") Instant joinedAt
) {
}
