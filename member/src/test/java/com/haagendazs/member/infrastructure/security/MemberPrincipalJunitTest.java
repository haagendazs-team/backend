package com.haagendazs.member.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberPrincipalJunitTest {

    @Test
    @DisplayName("[Happy] UserDetails 메서드가 MemberPrincipal 정보를 반환한다")
    void userDetailsMethods_returnExpectedValues() {
        MemberPrincipal principal = new MemberPrincipal(1L, "example@example.com");

        assertThat(principal.getMemberId()).isEqualTo(1L);
        assertThat(principal.getEmail()).isEqualTo("example@example.com");
        assertThat(principal.getUsername()).isEqualTo("example@example.com");
        assertThat(principal.getPassword()).isNull();
        assertThat(principal.getAuthorities()).isEmpty();
        assertThat(principal.isAccountNonExpired()).isTrue();
        assertThat(principal.isAccountNonLocked()).isTrue();
        assertThat(principal.isCredentialsNonExpired()).isTrue();
        assertThat(principal.isEnabled()).isTrue();
    }
}
