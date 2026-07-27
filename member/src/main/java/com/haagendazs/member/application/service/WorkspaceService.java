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
import com.haagendazs.member.infrastructure.kafka.TransactionAfterCommitExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final MemberRepository memberRepository;
    private final MembershipEventPublisher membershipEventPublisher;
    private final SearchIndexEventPublisher searchIndexEventPublisher;
    private final ChatEventPublisher chatEventPublisher;
    private final PaymentEventPublisher paymentEventPublisher;
    private final TransactionAfterCommitExecutor afterCommitExecutor;

    @Transactional
    public WorkspaceResult createWorkspace(Long memberId, String name, String iconUrl) {
        Workspace workspace = workspaceRepository.save(Workspace.create(name, iconUrl, "FREE"));
        workspaceMemberRepository.save(WorkspaceMember.assign(workspace.getWorkspaceId(), memberId, WorkspaceRole.OWNER));
        Member owner = getMemberOrThrow(memberId);
        afterCommitExecutor.runAfterCommit(() -> {
                membershipEventPublisher.publishWorkspaceJoined(memberId, workspace.getWorkspaceId());
                chatEventPublisher.publishWorkspaceMemberJoined(owner, workspace.getWorkspaceId());
                paymentEventPublisher.publishWorkspaceCreated(workspace.getWorkspaceId());
        });
        return WorkspaceResult.from(workspace);
    }

    public List<WorkspaceResult> getMyWorkspaces(Long memberId) {
        List<WorkspaceMember> memberships = workspaceMemberRepository.findAllByMemberId(memberId);
        if (memberships.isEmpty()) {
            return List.of();
        }

        List<Long> workspaceIds = memberships.stream()
                .map(WorkspaceMember::getWorkspaceId)
                .toList();

        Map<Long, Workspace> workspaceById = workspaceRepository.findAllByIdIn(workspaceIds).stream()
                .collect(Collectors.toMap(Workspace::getWorkspaceId, Function.identity()));

        return workspaceIds.stream()
                .map(workspaceId -> {
                    Workspace workspace = workspaceById.get(workspaceId);
                    if (workspace == null) {
                        throw new BusinessException(MemberErrorCode.WORKSPACE_NOT_FOUND);
                    }
                    return WorkspaceResult.from(workspace);
                })
                .toList();
    }

    public WorkspaceResult getWorkspace(Long memberId, Long workspaceId) {
        validateWorkspaceMember(memberId, workspaceId);
        Workspace workspace = getWorkspaceOrThrow(workspaceId);
        return WorkspaceResult.from(workspace);
    }

    @Transactional
    public WorkspaceResult updateWorkspace(Long memberId, Long workspaceId, String name, String iconUrl) {
        WorkspaceMember workspaceMember = validateWorkspaceMember(memberId, workspaceId);
        validateManagePermission(workspaceMember);

        Workspace workspace = getWorkspaceOrThrow(workspaceId);
        workspace.updateInfo(name, iconUrl);
        return WorkspaceResult.from(workspaceRepository.save(workspace));
    }

    @Transactional
    public void deleteWorkspace(Long memberId, Long workspaceId) {
        WorkspaceMember workspaceMember = validateWorkspaceMember(memberId, workspaceId);
        if (workspaceMember.getWorkspaceRole() != WorkspaceRole.OWNER) {
            throw new BusinessException(MemberErrorCode.INSUFFICIENT_PERMISSION);
        }

        afterCommitExecutor.runAfterCommit(() -> searchIndexEventPublisher.publishWorkspaceDeleted(workspaceId));
        workspaceRepository.delete(getWorkspaceOrThrow(workspaceId));
    }

    @Transactional
    public WorkspaceMemberResult inviteMember(Long requesterId, Long workspaceId, String email, String role) {
        WorkspaceMember requester = validateWorkspaceMember(requesterId, workspaceId);
        validateManagePermission(requester);

        Member targetMember = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));

        if (requesterId.equals(targetMember.getMemberId())) {
            throw new BusinessException(MemberErrorCode.CANNOT_INVITE_SELF);
        }

        if (workspaceMemberRepository.findByWorkspaceIdAndMemberId(workspaceId, targetMember.getMemberId()).isPresent()) {
            throw new BusinessException(MemberErrorCode.ALREADY_WORKSPACE_MEMBER);
        }

        WorkspaceRole workspaceRole = resolveInviteRole(role);
        WorkspaceMember invited = workspaceMemberRepository.save(
                WorkspaceMember.assign(workspaceId, targetMember.getMemberId(), workspaceRole)
        );
        afterCommitExecutor.runAfterCommit(() -> {
                membershipEventPublisher.publishWorkspaceJoined(targetMember.getMemberId(), workspaceId);
                chatEventPublisher.publishWorkspaceMemberJoined(targetMember, workspaceId);
        });
        return WorkspaceMemberResult.from(invited);
    }

    @Transactional
    public void leaveWorkspace(Long memberId, Long workspaceId) {
        WorkspaceMember workspaceMember = validateWorkspaceMember(memberId, workspaceId);
        if (workspaceMember.getWorkspaceRole() == WorkspaceRole.OWNER) {
            throw new BusinessException(MemberErrorCode.INSUFFICIENT_PERMISSION);
        }

        workspaceMemberRepository.deleteByWorkspaceIdAndMemberId(workspaceId, memberId);
        afterCommitExecutor.runAfterCommit(() ->
                membershipEventPublisher.publishWorkspaceLeft(memberId, workspaceId));
    }

    public List<WorkspaceMemberResult> getWorkspaceMembers(Long memberId, Long workspaceId) {
        validateWorkspaceMember(memberId, workspaceId);
        return workspaceMemberRepository.findAllByWorkspaceId(workspaceId).stream()
                .map(WorkspaceMemberResult::from)
                .toList();
    }

    WorkspaceMember validateWorkspaceMember(Long memberId, Long workspaceId) {
        getWorkspaceOrThrow(workspaceId);
        return workspaceMemberRepository.findByWorkspaceIdAndMemberId(workspaceId, memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.NOT_WORKSPACE_MEMBER));
    }

    private Workspace getWorkspaceOrThrow(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.WORKSPACE_NOT_FOUND));
    }

    private void validateManagePermission(WorkspaceMember workspaceMember) {
        if (!workspaceMember.getWorkspaceRole().canManageWorkspace()) {
            throw new BusinessException(MemberErrorCode.INSUFFICIENT_PERMISSION);
        }
    }

    private WorkspaceRole resolveInviteRole(String role) {
        if (role == null || role.isBlank()) {
            return WorkspaceRole.MEMBER;
        }
        final WorkspaceRole workspaceRole;
        try {
            workspaceRole = WorkspaceRole.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(MemberErrorCode.INVALID_INVITE_ROLE);
        }
        if (!workspaceRole.isAssignableOnInvite()) {
            throw new BusinessException(MemberErrorCode.INVALID_INVITE_ROLE);
        }
        return workspaceRole;
    }

    private Member getMemberOrThrow(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    }
}
