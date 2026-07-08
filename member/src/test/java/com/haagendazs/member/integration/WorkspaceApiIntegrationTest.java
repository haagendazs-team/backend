package com.haagendazs.member.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkspaceApiIntegrationTest extends AbstractIntegrationTest {

    private AuthTokens ownerTokens;
    private AuthTokens memberTokens;

    @BeforeEach
    void setUp() throws Exception {
        ownerTokens = signupAndLogin("example@example.com", "password123", "user");
        memberTokens = signupAndLogin("example2@example.com", "password123", "user2");
    }

    @Test
    @DisplayName("[Happy] 워크스페이스 생성, 조회, 수정, 멤버 초대, 삭제 흐름이 정상 동작한다")
    void workspaceFlow_success() throws Exception {
        String createResponse = mockMvc.perform(post("/workspaces")
                        .header("Authorization", "Bearer " + ownerTokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "example",
                                  "iconUrl": "https://image.example.com/workspace.png"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("example"))
                .andExpect(jsonPath("$.data.subscription").value("FREE"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long workspaceId = objectMapper.readTree(createResponse).get("data").get("workspaceId").asLong();

        mockMvc.perform(get("/workspaces/my")
                        .header("Authorization", "Bearer " + ownerTokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].workspaceId").value(workspaceId));

        mockMvc.perform(get("/workspaces/{workspaceId}", workspaceId)
                        .header("Authorization", "Bearer " + ownerTokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("example"));

        mockMvc.perform(patch("/workspaces/{workspaceId}", workspaceId)
                        .header("Authorization", "Bearer " + ownerTokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "example2",
                                  "iconUrl": "https://image.example.com/new.png"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("example2"));

        mockMvc.perform(post("/workspaces/{workspaceId}/members", workspaceId)
                        .header("Authorization", "Bearer " + ownerTokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "example2@example.com",
                                  "role": "MEMBER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("MEMBER"));

        mockMvc.perform(get("/workspaces/{workspaceId}/members", workspaceId)
                        .header("Authorization", "Bearer " + ownerTokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(delete("/workspaces/{workspaceId}", workspaceId)
                        .header("Authorization", "Bearer " + ownerTokens.accessToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("[Exception] 워크스페이스 멤버가 아닌 사용자는 조회할 수 없다")
    void getWorkspace_notMember_returnsForbidden() throws Exception {
        String createResponse = mockMvc.perform(post("/workspaces")
                        .header("Authorization", "Bearer " + ownerTokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "example",
                                  "iconUrl": null
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long workspaceId = objectMapper.readTree(createResponse).get("data").get("workspaceId").asLong();

        mockMvc.perform(get("/workspaces/{workspaceId}", workspaceId)
                        .header("Authorization", "Bearer " + memberTokens.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("M008"));
    }

    @Test
    @DisplayName("[Exception] MEMBER 권한 사용자는 워크스페이스를 수정할 수 없다")
    void updateWorkspace_memberRole_returnsForbidden() throws Exception {
        String createResponse = mockMvc.perform(post("/workspaces")
                        .header("Authorization", "Bearer " + ownerTokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "example",
                                  "iconUrl": null
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long workspaceId = objectMapper.readTree(createResponse).get("data").get("workspaceId").asLong();

        mockMvc.perform(post("/workspaces/{workspaceId}/members", workspaceId)
                        .header("Authorization", "Bearer " + ownerTokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "example2@example.com",
                                  "role": "MEMBER"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/workspaces/{workspaceId}", workspaceId)
                        .header("Authorization", "Bearer " + memberTokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "example3",
                                  "iconUrl": null
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("M010"));
    }
}
