package com.haagendazs.common.event.search;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 프로필 변경 시 Search 도메인 member_indexes 동기화용 Kafka 페이로드.
 */
public record MemberUpdatedPayload(
        @JsonProperty("member_id") Long memberId,
        @JsonProperty("email") String email,
        @JsonProperty("nickname") String nickname,
        @JsonProperty("profile_image_url") String profileImageUrl
) {
}
