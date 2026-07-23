package com.haagendazs.common.event.search;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record MemberChannelCreatedPayload(
        @JsonProperty("channel_id") Long channelId,
        @JsonProperty("workspace_id") Long workspaceId,
        @JsonProperty("name") String name,
        @JsonProperty("is_direct_message") Boolean isDirectMessage,
        @JsonProperty("created_at") Instant createdAt
) {
}
