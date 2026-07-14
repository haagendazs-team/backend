package com.haagendazs.member.application.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.member.application.dto.WorkspaceMemberResult;
import com.haagendazs.member.application.dto.WorkspaceResult;
import com.haagendazs.member.application.port.ChatEventPublisher;
import com.haagendazs.member.application.port.MembershipEventPublisher;
import com.haagendazs.member.application.port.PaymentEventPublisher;
import com.haagendazs.member.application.port.SearchIndexEventPublisher;
import com.haagendazs.member.domain.exception.MemberErrorCode;
import com.haagendazs.member.domain.model.Member;
import com.haagendazs.member.domain.model.Workspace;
import com.haagendazs.member.domain.model.WorkspaceMember;
import com.haagendazs.member.domain.model.WorkspaceRole;
import com.haagendazs.member.domain.repository.MemberRepository;
import com.haagendazs.member.domain.repository.WorkspaceMemberRepository;
import com.haagendazs.member.domain.repository.WorkspaceRepository;
import com.haagendazs.member.fixture.TestFixture;
import com.haagendazs.member.infrastructure.kafka.TransactionAfterCommitExecutor;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceJunitTest {

    private static final String WORKSPACE_NAME = "example";
    private static final String INVITE_EMAIL = "example2@example.com";
    private static final String INVITE_NICKNAME = "user2";

    @InjectMocks
    private WorkspaceService workspaceService;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MembershipEventPublisher membershipEventPublisher;

    @Mock
    private SearchIndexEventPublisher searchIndexEventPublisher;

    @Mock
    private ChatEventPublisher chatEventPublisher;

    @Mock
    private PaymentEventPublisher paymentEventPublisher;

    @Mock
    private TransactionAfterCommitExecutor afterCommitExecutor;

    @Test
    @DisplayName("[Happy] 워크스페이스 생성 시 생성자를 OWNER로 등록한다")
    void createWorkspace_success_assignsOwnerRole() {
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> {
            Workspace workspace = invocation.getArgument(0);
            return TestFixture.workspace(1L, workspace.getName());
        });
        when(workspaceMemberRepository.save(any(WorkspaceMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(TestFixture.member(1L, "example@example.com", "encoded", "user")));
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(0);
            action.run();
            return null;
        }).when(afterCommitExecutor).runAfterCommit(any(Runnable.class));

        WorkspaceResult result = workspaceService.createWorkspace(1L, WORKSPACE_NAME, null);

        assertThat(result.workspaceId()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo(WORKSPACE_NAME);
        assertThat(result.subscription()).isEqualTo("FREE");
        verify(membershipEventPublisher).publishWorkspaceJoined(1L, 1L);
        verify(chatEventPublisher).publishWorkspaceMemberJoined(any(Member.class), eq(1L));
        verify(paymentEventPublisher).publishWorkspaceCreated(1L);
    }

    @Test
    @DisplayName("[Happy] 워크스페이스 조회에 성공하면 워크스페이스 정보를 반환한다")
    void getWorkspace_success_returnsWorkspace() {
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(TestFixture.workspace(1L, WORKSPACE_NAME)));
        when(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L))
                .thenReturn(Optional.of(TestFixture.workspaceMember(1L, 1L, WorkspaceRole.OWNER)));

        WorkspaceResult result = workspaceService.getWorkspace(1L, 1L);

        assertThat(result.workspaceId()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo(WORKSPACE_NAME);
    }

    @Test
    @DisplayName("[Happy] ADMIN은 워크스페이스 정보 수정에 성공한다")
    void updateWorkspace_admin_success_returnsUpdatedWorkspace() {
        Workspace workspace = TestFixture.workspace(1L, WORKSPACE_NAME);
        WorkspaceMember admin = TestFixture.workspaceMember(1L, 2L, WorkspaceRole.ADMIN);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 2L)).thenReturn(Optional.of(admin));
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkspaceResult result = workspaceService.updateWorkspace(2L, 1L, "example2", "https://icon.example.com/icon.png");

        assertThat(result.name()).isEqualTo("example2");
        assertThat(result.iconUrl()).isEqualTo("https://icon.example.com/icon.png");
    }

    @Test
    @DisplayName("[Exception] 워크스페이스 멤버가 아니면 조회 시 NOT_WORKSPACE_MEMBER 예외가 발생한다")
    void getWorkspace_notMember_throwsException() {
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(TestFixture.workspace(1L, WORKSPACE_NAME)));
        when(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.getWorkspace(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.NOT_WORKSPACE_MEMBER);
    }

    @Test
    @DisplayName("[Exception] OWNER 또는 ADMIN만 워크스페이스 정보를 수정할 수 있다")
    void updateWorkspace_memberRole_throwsInsufficientPermission() {
        WorkspaceMember member = TestFixture.workspaceMember(1L, 2L, WorkspaceRole.MEMBER);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(TestFixture.workspace(1L, WORKSPACE_NAME)));
        when(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 2L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> workspaceService.updateWorkspace(2L, 1L, "example2", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.INSUFFICIENT_PERMISSION);
    }

    @Test
    @DisplayName("[Exception] OWNER만 워크스페이스를 삭제할 수 있다")
    void deleteWorkspace_adminRole_throwsInsufficientPermission() {
        WorkspaceMember admin = TestFixture.workspaceMember(1L, 2L, WorkspaceRole.ADMIN);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(TestFixture.workspace(1L, WORKSPACE_NAME)));
        when(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 2L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> workspaceService.deleteWorkspace(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.INSUFFICIENT_PERMISSION);
    }

    @Test
    @DisplayName("[Happy] OWNER가 워크스페이스 삭제에 성공하면 워크스페이스를 삭제한다")
    void deleteWorkspace_owner_success_deletesWorkspace() {
        Workspace workspace = TestFixture.workspace(1L, WORKSPACE_NAME);
        WorkspaceMember owner = TestFixture.workspaceMember(1L, 1L, WorkspaceRole.OWNER);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L)).thenReturn(Optional.of(owner));
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(0);
            action.run();
            return null;
        }).when(afterCommitExecutor).runAfterCommit(any(Runnable.class));

        workspaceService.deleteWorkspace(1L, 1L);

        verify(workspaceRepository).delete(workspace);
        verify(searchIndexEventPublisher).publishWorkspaceDeleted(1L);
    }

    @Test
    @DisplayName("[Happy] 멤버 초대에 성공하면 초대된 멤버 정보를 반환한다")
    void inviteMember_success_returnsInvitedMember() {
        WorkspaceMember owner = TestFixture.workspaceMember(1L, 1L, WorkspaceRole.OWNER);
        Member target = TestFixture.member(2L, INVITE_EMAIL, "encoded", INVITE_NICKNAME);

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(TestFixture.workspace(1L, WORKSPACE_NAME)));
        when(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L)).thenReturn(Optional.of(owner));
        when(memberRepository.findByEmail(INVITE_EMAIL)).thenReturn(Optional.of(target));
        when(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 2L)).thenReturn(Optional.empty());
        when(workspaceMemberRepository.save(any(WorkspaceMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(0);
            action.run();
            return null;
        }).when(afterCommitExecutor).runAfterCommit(any(Runnable.class));

        WorkspaceMemberResult result = workspaceService.inviteMember(1L, 1L, INVITE_EMAIL, "MEMBER");

        assertThat(result.memberId()).isEqualTo(2L);
        assertThat(result.role()).isEqualTo("MEMBER");
        verify(membershipEventPublisher).publishWorkspaceJoined(2L, 1L);
        verify(chatEventPublisher).publishWorkspaceMemberJoined(target, 1L);
    }

    @Test
    @DisplayName("[Exception] 이미 워크스페이스에 속한 멤버를 초대하면 ALREADY_WORKSPACE_MEMBER 예외가 발생한다")
    void inviteMember_alreadyMember_throwsException() {
        WorkspaceMember owner = TestFixture.workspaceMember(1L, 1L, WorkspaceRole.OWNER);
        Member target = TestFixture.member(2L, INVITE_EMAIL, "encoded", INVITE_NICKNAME);
        WorkspaceMember existing = TestFixture.workspaceMember(1L, 2L, WorkspaceRole.MEMBER);

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(TestFixture.workspace(1L, WORKSPACE_NAME)));
        when(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L)).thenReturn(Optional.of(owner));
        when(memberRepository.findByEmail(INVITE_EMAIL)).thenReturn(Optional.of(target));
        when(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 2L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> workspaceService.inviteMember(1L, 1L, INVITE_EMAIL, "MEMBER"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.ALREADY_WORKSPACE_MEMBER);
    }

    @Test
    @DisplayName("[Happy] 내 워크스페이스 목록 조회에 성공하면 소속 워크스페이스 목록을 반환한다")
    void getMyWorkspaces_success_returnsWorkspaceList() {
        when(workspaceMemberRepository.findAllByMemberId(1L))
                .thenReturn(List.of(TestFixture.workspaceMember(1L, 1L, WorkspaceRole.OWNER)));
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(TestFixture.workspace(1L, WORKSPACE_NAME)));

        List<WorkspaceResult> results = workspaceService.getMyWorkspaces(1L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).isEqualTo(WORKSPACE_NAME);
    }

    @Test
    @DisplayName("[Exception] OWNER는 워크스페이스 탈퇴 시 INSUFFICIENT_PERMISSION 예외가 발생한다")
    void leaveWorkspace_owner_throwsInsufficientPermission() {
        WorkspaceMember owner = TestFixture.workspaceMember(1L, 1L, WorkspaceRole.OWNER);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(TestFixture.workspace(1L, WORKSPACE_NAME)));
        when(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> workspaceService.leaveWorkspace(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.INSUFFICIENT_PERMISSION);
    }

    @Test
    @DisplayName("[Exception] 존재하지 않는 이메일로 초대하면 MEMBER_NOT_FOUND 예외가 발생한다")
    void inviteMember_unknownEmail_throwsException() {
        WorkspaceMember owner = TestFixture.workspaceMember(1L, 1L, WorkspaceRole.OWNER);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(TestFixture.workspace(1L, WORKSPACE_NAME)));
        when(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L)).thenReturn(Optional.of(owner));
        when(memberRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.inviteMember(1L, 1L, "unknown@example.com", "MEMBER"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("[Exception] 자기 자신을 초대하면 CANNOT_INVITE_SELF 예외가 발생한다")
    void inviteMember_self_throwsException() {
        WorkspaceMember owner = TestFixture.workspaceMember(1L, 1L, WorkspaceRole.OWNER);
        Member ownerMember = TestFixture.member(1L, "example@example.com", "encoded", "user");
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(TestFixture.workspace(1L, WORKSPACE_NAME)));
        when(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 1L)).thenReturn(Optional.of(owner));
        when(memberRepository.findByEmail("example@example.com")).thenReturn(Optional.of(ownerMember));

        assertThatThrownBy(() -> workspaceService.inviteMember(1L, 1L, "example@example.com", "MEMBER"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.CANNOT_INVITE_SELF);
    }

    @Test
    @DisplayName("[Happy] 워크스페이스 탈퇴에 성공하면 멤버십을 삭제하고 Kafka 이벤트를 발행한다")
    void leaveWorkspace_success_deletesMembershipAndPublishesEvent() {
        WorkspaceMember member = TestFixture.workspaceMember(1L, 2L, WorkspaceRole.MEMBER);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(TestFixture.workspace(1L, WORKSPACE_NAME)));
        when(workspaceMemberRepository.findByWorkspaceIdAndMemberId(1L, 2L)).thenReturn(Optional.of(member));
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(0);
            action.run();
            return null;
        }).when(afterCommitExecutor).runAfterCommit(any(Runnable.class));

        workspaceService.leaveWorkspace(2L, 1L);

        verify(workspaceMemberRepository).deleteByWorkspaceIdAndMemberId(1L, 2L);
        verify(membershipEventPublisher).publishWorkspaceLeft(2L, 1L);
    }
}
