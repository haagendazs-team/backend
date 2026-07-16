package com.haagendazs.common.event.search;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record MemberChannelDeletedPayload(
        @JsonProperty("channel_id") Long channelId,
        @JsonProperty("deleted_at") Instant deletedAt
) {
}
