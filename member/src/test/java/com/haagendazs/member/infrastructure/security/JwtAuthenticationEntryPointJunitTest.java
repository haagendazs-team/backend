package com.haagendazs.member.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.common.exception.CommonErrorCode;
import com.haagendazs.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationEntryPointJunitTest {

    private final JwtAuthenticationEntryPoint entryPoint =
            new JwtAuthenticationEntryPoint(new ObjectMapper());

    @Test
    @DisplayName("[Happy] 인증 실패 시 401 Unauthorized JSON 응답을 반환한다")
    void commence_returnsUnauthorizedJsonResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("invalid"));

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        ApiResponse<?> body = new ObjectMapper().readValue(response.getContentAsString(), ApiResponse.class);
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED.getCode());
    }
}
