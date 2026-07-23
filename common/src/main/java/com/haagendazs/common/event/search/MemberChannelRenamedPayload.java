package com.haagendazs.common.event.search;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record MemberChannelRenamedPayload(
        @JsonProperty("channel_id") Long channelId,
        @JsonProperty("name") String name,
        @JsonProperty("renamed_at") Instant renamedAt
) {
}
