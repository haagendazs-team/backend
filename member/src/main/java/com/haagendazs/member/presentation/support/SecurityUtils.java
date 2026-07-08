package com.haagendazs.member.presentation.support;

import com.haagendazs.member.infrastructure.security.MemberPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Long getCurrentMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof MemberPrincipal principal)) {
            throw new IllegalStateException("인증 정보가 없습니다.");
        }
        return principal.getMemberId();
    }
}
