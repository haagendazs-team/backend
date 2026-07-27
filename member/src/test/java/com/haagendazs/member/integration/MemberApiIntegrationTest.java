package com.haagendazs.member.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MemberApiIntegrationTest extends AbstractIntegrationTest {

    private AuthTokens tokens;

    @BeforeEach
    void setUp() throws Exception {
        tokens = signupAndLogin("example@example.com", "password123", "user");
    }

    @Test
    @DisplayName("[Happy] 내 프로필 조회에 성공한다")
    void getMyProfile_success() throws Exception {
        mockMvc.perform(get("/members/me")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("example@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("user"));
    }

    @Test
    @DisplayName("[Happy] 프로필 수정에 성공한다")
    void updateMyProfile_success() throws Exception {
        mockMvc.perform(patch("/members/me")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "user2",
                                  "profileImageUrl": "https://image.example.com/profile.png"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("user2"))
                .andExpect(jsonPath("$.data.profileImageUrl").value("https://image.example.com/profile.png"));
    }

    @Test
    @DisplayName("[Happy] 다른 회원 프로필 조회에 성공한다")
    void getMemberProfile_success() throws Exception {
        AuthTokens otherTokens = signupAndLogin("example2@example.com", "password123", "user2");

        mockMvc.perform(get("/members/{memberId}", getMemberId(otherTokens.accessToken()))
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("example2@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("user2"));
    }

    @Test
    @DisplayName("[Exception] 인증 없이 회원 API를 호출하면 401 Unauthorized를 반환한다")
    void memberApi_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C004"));
    }

    @Test
    @DisplayName("[Exception] 존재하지 않는 회원 프로필 조회 시 404 Not Found를 반환한다")
    void getMemberProfile_notFound_returnsNotFound() throws Exception {
        mockMvc.perform(get("/members/{memberId}", 999L)
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("M003"));
    }

    @Test
    @DisplayName("[Happy] 회원 탈퇴에 성공한다")
    void withdraw_success() throws Exception {
        mockMvc.perform(delete("/members/me")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("[Exception] 탈퇴한 회원은 Access JWT가 남아 있어도 401 Unauthorized를 반환한다")
    void getMyProfile_afterWithdraw_returnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/members/me")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isNoContent());

        // 필터에서 비활성 회원은 인증 컨텍스트를 만들지 않음 → 보호 API는 401
        mockMvc.perform(get("/members/me")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C004"));
    }
}
