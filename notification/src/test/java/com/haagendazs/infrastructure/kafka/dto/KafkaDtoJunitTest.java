package com.haagendazs.infrastructure.kafka.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaDtoJunitTest {

    @Test
    @DisplayName("EmailCertPayload 생성 시 email과 code가 설정된다")
    void emailCertPayload_createsWithEmailAndCode() {
        EmailCertPayload payload = new EmailCertPayload("test@example.com", "123456");

        assertThat(payload.email()).isEqualTo("test@example.com");
        assertThat(payload.code()).isEqualTo("123456");
    }

    @Test
    @DisplayName("MemberCreatedPayload 생성 시 모든 필드가 설정된다")
    void memberCreatedPayload_createsWithAllFields() {
        MemberCreatedPayload payload = new MemberCreatedPayload(1L, "user@example.com", "닉네임", "https://img.url");

        assertThat(payload.memberId()).isEqualTo(1L);
        assertThat(payload.email()).isEqualTo("user@example.com");
        assertThat(payload.nickname()).isEqualTo("닉네임");
        assertThat(payload.profileImageUrl()).isEqualTo("https://img.url");
    }
}
