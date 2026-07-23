package com.haagendazs.member.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("[Exception] 회원가입 Validation 실패 시 400 Bad Request를 반환한다")
    void signup_invalidEmail_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email",
                                  "password": "password123",
                                  "nickname": "user"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("[Happy] 로그아웃에 성공하면 204 No Content를 반환한다")
    void logout_success_returnsNoContent() throws Exception {
        AuthTokens tokens = signupAndLogin("logout@example.com", "password123", "user");

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("[Happy] Refresh Token 재발급에 성공한다")
    void reissue_success_returnsNewTokens() throws Exception {
        AuthTokens tokens = signupAndLogin("reissue@example.com", "password123", "user");

        mockMvc.perform(post("/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(tokens.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }
}
