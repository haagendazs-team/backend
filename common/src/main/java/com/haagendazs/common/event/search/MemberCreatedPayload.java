package com.haagendazs.common.event.search;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 회원가입 시 Search 도메인 member_indexes 적재용 Kafka 페이로드.
 * password 등 민감 정보는 포함하지 않는다.
 */
public record MemberCreatedPayload(
        @JsonProperty("member_id") Long memberId,
        @JsonProperty("email") String email,
        @JsonProperty("nickname") String nickname,
        @JsonProperty("profile_image_url") String profileImageUrl
) {
}
