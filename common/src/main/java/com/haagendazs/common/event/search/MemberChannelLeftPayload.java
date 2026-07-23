package com.haagendazs.common.event.search;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record MemberChannelLeftPayload(
        @JsonProperty("member_id") Long memberId,
        @JsonProperty("channel_id") Long channelId,
        @JsonProperty("left_at") Instant leftAt
) {
}
