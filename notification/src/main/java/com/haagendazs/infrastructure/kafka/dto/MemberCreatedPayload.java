package com.haagendazs.infrastructure.kafka.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MemberCreatedPayload(
        @JsonProperty("member_id") Long memberId,
        String email,
        String nickname,
        @JsonProperty("profile_image_url") String profileImageUrl
) {}
