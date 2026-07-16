package com.haagendazs.common.event.notification;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MemberEmailCertPayload(
        @JsonProperty("email") String email,
        @JsonProperty("code") String code
) {
}
