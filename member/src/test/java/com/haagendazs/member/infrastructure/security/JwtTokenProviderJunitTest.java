package com.haagendazs.member.infrastructure.security;

import com.haagendazs.member.infrastructure.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderJunitTest {

    private static final String EMAIL = "example@example.com";

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-key-for-jwt-signing-must-be-long-enough");
        jwtProperties.setAccessExpirationMs(1_800_000L);
        jwtTokenProvider = new JwtTokenProvider(jwtProperties);
    }

    @Test
    @DisplayName("[Happy] Access Token을 생성하고 검증·파싱할 수 있다")
    void createAccessToken_andValidate_success() {
        String token = jwtTokenProvider.createAccessToken(1L, EMAIL);

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getMemberId(token)).isEqualTo(1L);
        assertThat(jwtTokenProvider.getEmail(token)).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("[Happy] Refresh Token은 UUID 형식 문자열을 반환한다")
    void createRefreshToken_returnsUuidString() {
        String refreshToken = jwtTokenProvider.createRefreshToken();

        assertThat(refreshToken).isNotBlank();
        assertThat(refreshToken).matches("[0-9a-f\\-]{36}");
    }

    @Test
    @DisplayName("[Exception] 유효하지 않은 토큰은 validateToken에서 false를 반환한다")
    void validateToken_invalidToken_returnsFalse() {
        assertThat(jwtTokenProvider.validateToken("invalid-token")).isFalse();
    }
}
