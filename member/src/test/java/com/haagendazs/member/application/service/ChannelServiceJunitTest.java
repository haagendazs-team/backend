package com.haagendazs.member.application.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.member.application.dto.ChannelResult;
import com.haagendazs.member.domain.exception.MemberErrorCode;
import com.haagendazs.member.domain.model.Channel;
import com.haagendazs.member.domain.model.ChannelMember;
import com.haagendazs.member.domain.model.WorkspaceMember;
import com.haagendazs.member.domain.model.WorkspaceRole;
import com.haagendazs.member.domain.repository.ChannelMemberRepository;
import com.haagendazs.member.domain.repository.ChannelRepository;
import com.haagendazs.member.domain.repository.WorkspaceMemberRepository;
import com.haagendazs.member.fixture.TestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelServiceJunitTest {

    private static final String CHANNEL_NAME = "example";

    @InjectMocks
    private ChannelService channelService;

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private ChannelMemberRepository channelMemberRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private WorkspaceService workspaceService;

    @Test
    @DisplayName("[Happy] 채널 생성 시 생성자를 채널 멤버로 등록한다")
    void createChannel_success_addsCreatorAsMember() {
        WorkspaceMember workspaceMember = TestFixture.workspaceMember(1L, 1L, WorkspaceRole.OWNER);
        when(workspaceService.validateWorkspaceMember(1L, 1L)).thenReturn(workspaceMember);
        when(channelRepository.save(any(Channel.class))).thenAnswer(invocation -> {
            Channel channel = invocation.getArgument(0);
            return TestFixture.channel(10L, channel.getWorkspaceId(), channel.getName(), false);
        });
        when(channelMemberRepository.save(any(ChannelMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChannelResult result = channelService.createChannel(1L, 1L, CHANNEL_NAME);

        assertThat(result.channelId()).isEqualTo(10L);
        assertThat(result.name()).isEqualTo(CHANNEL_NAME);
        assertThat(result.isDirectMessage()).isFalse();
    }

    @Test
    @DisplayName("[Exception] 채널 멤버가 아니면 채널 조회 시 NOT_CHANNEL_MEMBER 예외가 발생한다")
    void getChannel_notMember_throwsException() {
        Channel channel = TestFixture.channel(10L, 1L, CHANNEL_NAME, false);
        when(channelRepository.findById(10L)).thenReturn(Optional.of(channel));
        when(channelMemberRepository.existsByChannelIdAndMemberId(10L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> channelService.getChannel(2L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.NOT_CHANNEL_MEMBER);
    }

    @Test
    @DisplayName("[Happy] 워크스페이스 내 내 채널 목록 조회에 성공한다")
    void getMyChannelsInWorkspace_success_returnsJoinedChannels() {
        WorkspaceMember workspaceMember = TestFixture.workspaceMember(1L, 1L, WorkspaceRole.OWNER);
        Channel channel = TestFixture.channel(10L, 1L, CHANNEL_NAME, false);

        when(workspaceService.validateWorkspaceMember(1L, 1L)).thenReturn(workspaceMember);
        when(channelMemberRepository.findAllByMemberId(1L))
                .thenReturn(List.of(ChannelMember.add(10L, 1L)));
        when(channelRepository.findAllByWorkspaceIdAndChannelIdIn(eq(1L), anyList()))
                .thenReturn(List.of(channel));

        List<ChannelResult> results = channelService.getMyChannelsInWorkspace(1L, 1L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).isEqualTo(CHANNEL_NAME);
    }

    @Test
    @DisplayName("[Happy] DM 채널이 없으면 새 DM 채널을 생성한다")
    void getOrCreateDmChannel_notExists_createsDmChannel() {
        WorkspaceMember requester = TestFixture.workspaceMember(1L, 1L, WorkspaceRole.OWNER);
        WorkspaceMember target = TestFixture.workspaceMember(1L, 2L, WorkspaceRole.MEMBER);

        when(workspaceService.validateWorkspaceMember(1L, 1L)).thenReturn(requester);
        when(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 2L)).thenReturn(Optional.of(target));
        when(channelRepository.findDmChannelByWorkspaceIdAndMemberIds(1L, 1L, 2L)).thenReturn(Optional.empty());
        when(channelRepository.save(any(Channel.class))).thenAnswer(invocation -> {
            Channel channel = invocation.getArgument(0);
            return TestFixture.channel(20L, channel.getWorkspaceId(), channel.getName(), true);
        });
        when(channelMemberRepository.save(any(ChannelMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChannelResult result = channelService.getOrCreateDmChannel(1L, 1L, 2L);

        assertThat(result.channelId()).isEqualTo(20L);
        assertThat(result.isDirectMessage()).isTrue();
        assertThat(result.name()).isEqualTo("dm-1-2");
    }

    @Test
    @DisplayName("[Happy] 기존 DM 채널이 있으면 새로 생성하지 않고 기존 채널을 반환한다")
    void getOrCreateDmChannel_exists_returnsExistingChannel() {
        WorkspaceMember requester = TestFixture.workspaceMember(1L, 1L, WorkspaceRole.OWNER);
        WorkspaceMember target = TestFixture.workspaceMember(1L, 2L, WorkspaceRole.MEMBER);
        Channel existingDm = TestFixture.channel(20L, 1L, "dm-1-2", true);

        when(workspaceService.validateWorkspaceMember(1L, 1L)).thenReturn(requester);
        when(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 2L)).thenReturn(Optional.of(target));
        when(channelRepository.findDmChannelByWorkspaceIdAndMemberIds(1L, 1L, 2L))
                .thenReturn(Optional.of(existingDm));

        ChannelResult result = channelService.getOrCreateDmChannel(1L, 1L, 2L);

        assertThat(result.channelId()).isEqualTo(20L);
        verify(channelRepository).findDmChannelByWorkspaceIdAndMemberIds(1L, 1L, 2L);
    }

    @Test
    @DisplayName("[Happy] 채널 나가기에 성공하면 채널 멤버 정보를 삭제한다")
    void leaveChannel_success_deletesChannelMember() {
        when(channelMemberRepository.existsByChannelIdAndMemberId(10L, 1L)).thenReturn(true);

        channelService.leaveChannel(1L, 10L);

        verify(channelMemberRepository).deleteByChannelIdAndMemberId(10L, 1L);
    }

    @Test
    @DisplayName("[Exception] 자기 자신과 DM 채널을 생성하려 하면 CANNOT_INVITE_SELF 예외가 발생한다")
    void getOrCreateDmChannel_selfTarget_throwsException() {
        assertThatThrownBy(() -> channelService.getOrCreateDmChannel(1L, 1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.CANNOT_INVITE_SELF);
    }
}
