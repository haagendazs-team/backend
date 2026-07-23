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

class ChannelApiIntegrationTest extends AbstractIntegrationTest {

    private AuthTokens ownerTokens;
    private AuthTokens memberTokens;
    private Long workspaceId;

    @BeforeEach
    void setUp() throws Exception {
        ownerTokens = signupAndLogin("example@example.com", "password123", "user");
        memberTokens = signupAndLogin("example2@example.com", "password123", "user2");
        workspaceId = createWorkspaceAndInviteMember();
    }

    @Test
    @DisplayName("[Happy] 채널에 참여하지 않은 멤버는 빈 채널 목록을 조회한다")
    void getMyChannelsInWorkspace_noChannels_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/workspaces/{workspaceId}/channels", workspaceId)
                        .header("Authorization", "Bearer " + memberTokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("[Happy] 채널 삭제에 성공하면 채널과 멤버 정보가 함께 삭제된다")
    void deleteChannel_success() throws Exception {
        String createChannelResponse = mockMvc.perform(post("/workspaces/{workspaceId}/channels", workspaceId)
                        .header("Authorization", "Bearer " + ownerTokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "delete-target"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long channelId = objectMapper.readTree(createChannelResponse).get("data").get("channelId").asLong();

        mockMvc.perform(delete("/channels/{channelId}", channelId)
                        .header("Authorization", "Bearer " + ownerTokens.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/channels/{channelId}", channelId)
                        .header("Authorization", "Bearer " + ownerTokens.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("M011"));
    }

    @Test
    @DisplayName("[Happy] 채널 생성, 조회, 수정, 멤버 조회, 나가기 흐름이 정상 동작한다")
    void channelFlow_success() throws Exception {
        String createChannelResponse = mockMvc.perform(post("/workspaces/{workspaceId}/channels", workspaceId)
                        .header("Authorization", "Bearer " + ownerTokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "example"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("example"))
                .andExpect(jsonPath("$.data.isDirectMessage").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long channelId = objectMapper.readTree(createChannelResponse).get("data").get("channelId").asLong();

        mockMvc.perform(get("/workspaces/{workspaceId}/channels", workspaceId)
                        .header("Authorization", "Bearer " + ownerTokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].channelId").value(channelId));

        mockMvc.perform(get("/channels/{channelId}", channelId)
                        .header("Authorization", "Bearer " + ownerTokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("example"));

        mockMvc.perform(patch("/channels/{channelId}", channelId)
                        .header("Authorization", "Bearer " + ownerTokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "example2"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("example2"));

        mockMvc.perform(get("/channels/{channelId}/members", channelId)
                        .header("Authorization", "Bearer " + ownerTokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(delete("/channels/{channelId}/members", channelId)
                        .header("Authorization", "Bearer " + ownerTokens.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/channels/{channelId}", channelId)
                        .header("Authorization", "Bearer " + ownerTokens.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("M012"));
    }

    @Test
    @DisplayName("[Happy] DM 채널 조회 시 없으면 생성하고, 다시 조회하면 동일한 채널을 반환한다")
    void dmChannel_getOrCreate_returnsSameChannel() throws Exception {
        Long ownerId = getMemberId(ownerTokens.accessToken());
        Long targetMemberId = getMemberId(memberTokens.accessToken());
        String expectedDmName = "dm-" + Math.min(ownerId, targetMemberId) + "-" + Math.max(ownerId, targetMemberId);

        String firstResponse = mockMvc.perform(get("/workspaces/{workspaceId}/dms/{targetMemberId}", workspaceId, targetMemberId)
                        .header("Authorization", "Bearer " + ownerTokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDirectMessage").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long firstChannelId = objectMapper.readTree(firstResponse).get("data").get("channelId").asLong();

        mockMvc.perform(get("/workspaces/{workspaceId}/dms/{targetMemberId}", workspaceId, targetMemberId)
                        .header("Authorization", "Bearer " + ownerTokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channelId").value(firstChannelId))
                .andExpect(jsonPath("$.data.name").value(expectedDmName));
    }

    @Test
    @DisplayName("[Exception] 워크스페이스 멤버가 아닌 사용자는 채널을 생성할 수 없다")
    void createChannel_notWorkspaceMember_returnsForbidden() throws Exception {
        AuthTokens outsiderTokens = signupAndLogin("example3@example.com", "password123", "user3");

        mockMvc.perform(post("/workspaces/{workspaceId}/channels", workspaceId)
                        .header("Authorization", "Bearer " + outsiderTokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "example"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("M008"));
    }

    private Long createWorkspaceAndInviteMember() throws Exception {
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

        Long id = objectMapper.readTree(createResponse).get("data").get("workspaceId").asLong();

        mockMvc.perform(post("/workspaces/{workspaceId}/members", id)
                        .header("Authorization", "Bearer " + ownerTokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "example2@example.com",
                                  "role": "MEMBER"
                                }
                                """))
                .andExpect(status().isCreated());

        return id;
    }
}
