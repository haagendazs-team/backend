package com.haagendazs.common.event.search;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * 회원 탈퇴/비활성 시 Search 도메인 member_indexes 비활성 처리용 Kafka 페이로드.
 */
public record MemberDeactivatedPayload(
        @JsonProperty("member_id") Long memberId,
        @JsonProperty("deactivated_at") Instant deactivatedAt
) {
}
