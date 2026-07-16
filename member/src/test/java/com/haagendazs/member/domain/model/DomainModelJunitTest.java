package com.haagendazs.member.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DomainModelJunitTest {

    @Test
    @DisplayName("[Happy] Member.create는 활성 상태로 회원을 생성한다")
    void member_create_initializesActiveMember() {
        Member member = Member.create("example@example.com", "encoded", "user");

        assertThat(member.getEmail()).isEqualTo("example@example.com");
        assertThat(member.getIsActive()).isTrue();
        assertThat(member.getProfileImageUrl()).isNull();
    }

    @Test
    @DisplayName("[Happy] Member.updateProfile은 null이 아닌 필드만 수정한다")
    void member_updateProfile_updatesNonNullFieldsOnly() {
        Member member = Member.create("example@example.com", "encoded", "user");

        member.updateProfile("user2", null);
        assertThat(member.getNickname()).isEqualTo("user2");
        assertThat(member.getProfileImageUrl()).isNull();

        member.updateProfile(null, "https://image.example.com/profile.png");
        assertThat(member.getNickname()).isEqualTo("user2");
        assertThat(member.getProfileImageUrl()).isEqualTo("https://image.example.com/profile.png");
    }

    @Test
    @DisplayName("[Happy] Member.deactivate는 회원을 비활성화한다")
    void member_deactivate_setsInactive() {
        Member member = Member.create("example@example.com", "encoded", "user");

        member.deactivate();

        assertThat(member.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("[Happy] Token.isExpired는 만료 시각 기준으로 만료 여부를 판단한다")
    void token_isExpired_checksExpiration() {
        Token validToken = Token.create(1L, "refresh-token", LocalDateTime.now().plusHours(1));
        Token expiredToken = Token.create(1L, "refresh-token", LocalDateTime.now().minusMinutes(1));

        assertThat(validToken.isExpired()).isFalse();
        assertThat(expiredToken.isExpired()).isTrue();
    }

    @Test
    @DisplayName("[Happy] Channel.updateName은 null이 아닐 때만 이름을 변경한다")
    void channel_updateName_updatesWhenNotNull() {
        Channel channel = Channel.create(1L, "general", false);

        channel.updateName("random");
        assertThat(channel.getName()).isEqualTo("random");

        channel.updateName(null);
        assertThat(channel.getName()).isEqualTo("random");
    }

    @Test
    @DisplayName("[Happy] Workspace.create는 subscription 기본값을 FREE로 설정한다")
    void workspace_create_usesDefaultSubscription() {
        Workspace workspace = Workspace.create("example", null, null);

        assertThat(workspace.getName()).isEqualTo("example");
        assertThat(workspace.getSubscription()).isEqualTo("FREE");
    }

    @Test
    @DisplayName("[Happy] Workspace.updateInfo는 null이 아닌 필드만 수정한다")
    void workspace_updateInfo_updatesNonNullFieldsOnly() {
        Workspace workspace = Workspace.create("example", null, "FREE");

        workspace.updateInfo("example2", "https://icon.example.com/icon.png");

        assertThat(workspace.getName()).isEqualTo("example2");
        assertThat(workspace.getIconUrl()).isEqualTo("https://icon.example.com/icon.png");
    }

    @Test
    @DisplayName("[Happy] WorkspaceRole.canManageWorkspace는 OWNER/ADMIN만 true를 반환한다")
    void workspaceRole_canManageWorkspace() {
        assertThat(WorkspaceRole.OWNER.canManageWorkspace()).isTrue();
        assertThat(WorkspaceRole.ADMIN.canManageWorkspace()).isTrue();
        assertThat(WorkspaceRole.MEMBER.canManageWorkspace()).isFalse();
    }

    @Test
    @DisplayName("[Happy] WorkspaceMember.assign는 역할 문자열을 저장한다")
    void workspaceMember_assign_storesRoleValue() {
        WorkspaceMember workspaceMember = WorkspaceMember.assign(1L, 2L, WorkspaceRole.ADMIN);

        assertThat(workspaceMember.getWorkspaceId()).isEqualTo(1L);
        assertThat(workspaceMember.getMemberId()).isEqualTo(2L);
        assertThat(workspaceMember.getWorkspaceRole()).isEqualTo(WorkspaceRole.ADMIN);
    }

    @Test
    @DisplayName("[Happy] ChannelMember.add는 채널-멤버 관계를 생성한다")
    void channelMember_add_createsRelation() {
        ChannelMember channelMember = ChannelMember.add(10L, 1L);

        assertThat(channelMember.getChannelId()).isEqualTo(10L);
        assertThat(channelMember.getMemberId()).isEqualTo(1L);
    }
}
