package com.haagendazs.member.presentation.support;

import com.haagendazs.member.infrastructure.security.MemberPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityUtilsJunitTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("[Happy] 인증된 MemberPrincipal에서 memberId를 조회한다")
    void getCurrentMemberId_success() {
        MemberPrincipal principal = new MemberPrincipal(1L, "example@example.com");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        assertThat(SecurityUtils.getCurrentMemberId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("[Exception] 인증 정보가 없으면 IllegalStateException이 발생한다")
    void getCurrentMemberId_noAuthentication_throwsException() {
        assertThatThrownBy(SecurityUtils::getCurrentMemberId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("인증 정보가 없습니다.");
    }

    @Test
    @DisplayName("[Exception] Principal이 MemberPrincipal이 아니면 IllegalStateException이 발생한다")
    void getCurrentMemberId_invalidPrincipal_throwsException() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymous", null)
        );

        assertThatThrownBy(SecurityUtils::getCurrentMemberId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("인증 정보가 없습니다.");
    }
}
