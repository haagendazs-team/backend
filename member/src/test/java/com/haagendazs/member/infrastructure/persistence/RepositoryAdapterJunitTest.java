package com.haagendazs.member.infrastructure.persistence;

import com.haagendazs.member.domain.model.Channel;
import com.haagendazs.member.domain.model.ChannelMember;
import com.haagendazs.member.domain.model.WorkspaceMember;
import com.haagendazs.member.domain.model.WorkspaceRole;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositoryAdapterJunitTest {

    @Mock
    private ChannelJpaRepository channelJpaRepository;

    @Mock
    private ChannelMemberJpaRepository channelMemberJpaRepository;

    @Mock
    private WorkspaceMemberJpaRepository workspaceMemberJpaRepository;

    @InjectMocks
    private ChannelRepositoryAdapter channelRepositoryAdapter;

    @InjectMocks
    private ChannelMemberRepositoryAdapter channelMemberRepositoryAdapter;

    @InjectMocks
    private WorkspaceMemberRepositoryAdapter workspaceMemberRepositoryAdapter;

    @Test
    @DisplayName("[Happy] 채널 ID 목록이 비어 있으면 JPA 조회 없이 빈 목록을 반환한다")
    void channelRepository_findAllByWorkspaceIdAndChannelIdIn_emptyIds_returnsEmptyList() {
        List<Channel> channels = channelRepositoryAdapter.findAllByWorkspaceIdAndChannelIdIn(1L, List.of());

        assertThat(channels).isEmpty();
        verifyNoInteractions(channelJpaRepository);
    }

    @Test
    @DisplayName("[Happy] 채널 ID 목록이 있으면 JPA Repository로 조회한다")
    void channelRepository_findAllByWorkspaceIdAndChannelIdIn_delegatesToJpa() {
        Channel channel = TestFixture.channel(10L, 1L, "general", false);
        when(channelJpaRepository.findAllByWorkspaceIdAndChannelIdIn(1L, List.of(10L)))
                .thenReturn(List.of(channel));

        List<Channel> channels = channelRepositoryAdapter.findAllByWorkspaceIdAndChannelIdIn(1L, List.of(10L));

        assertThat(channels).containsExactly(channel);
    }

    @Test
    @DisplayName("[Happy] 채널 삭제는 JPA Repository에 위임한다")
    void channelRepository_delete_delegatesToJpa() {
        Channel channel = TestFixture.channel(10L, 1L, "general", false);

        channelRepositoryAdapter.delete(channel);

        verify(channelJpaRepository).delete(channel);
    }

    @Test
    @DisplayName("[Happy] 채널 ID 기준 멤버 전체 삭제는 JPA Repository에 위임한다")
    void channelMemberRepository_deleteAllByChannelId_delegatesToJpa() {
        channelMemberRepositoryAdapter.deleteAllByChannelId(10L);

        verify(channelMemberJpaRepository).deleteAllByChannelId(10L);
    }

    @Test
    @DisplayName("[Happy] 워크스페이스 멤버 삭제는 JPA Repository에 위임한다")
    void workspaceMemberRepository_deleteByWorkspaceIdAndMemberId_delegatesToJpa() {
        workspaceMemberRepositoryAdapter.deleteByWorkspaceIdAndMemberId(1L, 2L);

        verify(workspaceMemberJpaRepository).deleteByWorkspaceIdAndMemberId(1L, 2L);
    }

    @Test
    @DisplayName("[Happy] 워크스페이스 멤버 저장·조회는 JPA Repository에 위임한다")
    void workspaceMemberRepository_saveAndFind_delegatesToJpa() {
        WorkspaceMember workspaceMember = TestFixture.workspaceMember(1L, 2L, WorkspaceRole.MEMBER);
        when(workspaceMemberJpaRepository.save(workspaceMember)).thenReturn(workspaceMember);
        when(workspaceMemberJpaRepository.findByWorkspaceIdAndMemberId(1L, 2L))
                .thenReturn(Optional.of(workspaceMember));

        assertThat(workspaceMemberRepositoryAdapter.save(workspaceMember)).isEqualTo(workspaceMember);
        assertThat(workspaceMemberRepositoryAdapter.findByWorkspaceIdAndMemberId(1L, 2L))
                .contains(workspaceMember);
    }

    @Test
    @DisplayName("[Happy] 채널 멤버 저장·조회는 JPA Repository에 위임한다")
    void channelMemberRepository_saveAndFind_delegatesToJpa() {
        ChannelMember channelMember = ChannelMember.add(10L, 1L);
        when(channelMemberJpaRepository.save(channelMember)).thenReturn(channelMember);
        when(channelMemberJpaRepository.findAllByChannelId(10L)).thenReturn(List.of(channelMember));

        assertThat(channelMemberRepositoryAdapter.save(channelMember)).isEqualTo(channelMember);
        assertThat(channelMemberRepositoryAdapter.findAllByChannelId(10L)).containsExactly(channelMember);
    }
}
