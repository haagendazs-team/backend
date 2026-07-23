package com.haagendazs.member.presentation.controller;

import com.haagendazs.common.response.ApiResponse;
import com.haagendazs.member.application.service.WorkspaceService;
import com.haagendazs.member.presentation.dto.WorkspaceDto;
import com.haagendazs.member.presentation.support.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WorkspaceDto.WorkspaceResponse> createWorkspace(
            @Valid @RequestBody CreateWorkspaceRequest request
    ) {
        return ApiResponse.ok(WorkspaceDto.WorkspaceResponse.from(
                workspaceService.createWorkspace(
                        SecurityUtils.getCurrentMemberId(),
                        request.name(),
                        request.iconUrl()
                )
        ));
    }

    @GetMapping("/my")
    public ApiResponse<List<WorkspaceDto.WorkspaceResponse>> getMyWorkspaces() {
        List<WorkspaceDto.WorkspaceResponse> workspaces = workspaceService.getMyWorkspaces(SecurityUtils.getCurrentMemberId())
                .stream()
                .map(WorkspaceDto.WorkspaceResponse::from)
                .toList();
        return ApiResponse.ok(workspaces);
    }

    @GetMapping("/{workspaceId}")
    public ApiResponse<WorkspaceDto.WorkspaceResponse> getWorkspace(@PathVariable Long workspaceId) {
        return ApiResponse.ok(WorkspaceDto.WorkspaceResponse.from(
                workspaceService.getWorkspace(SecurityUtils.getCurrentMemberId(), workspaceId)
        ));
    }

    @PatchMapping("/{workspaceId}")
    public ApiResponse<WorkspaceDto.WorkspaceResponse> updateWorkspace(
            @PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceDto.UpdateWorkspaceRequest request
    ) {
        return ApiResponse.ok(WorkspaceDto.WorkspaceResponse.from(
                workspaceService.updateWorkspace(
                        SecurityUtils.getCurrentMemberId(),
                        workspaceId,
                        request.name(),
                        request.iconUrl()
                )
        ));
    }

    @DeleteMapping("/{workspaceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWorkspace(@PathVariable Long workspaceId) {
        workspaceService.deleteWorkspace(SecurityUtils.getCurrentMemberId(), workspaceId);
    }

    @PostMapping("/{workspaceId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WorkspaceDto.WorkspaceMemberResponse> inviteMember(
            @PathVariable Long workspaceId,
            @Valid @RequestBody InviteMemberRequest request
    ) {
        return ApiResponse.ok(WorkspaceDto.WorkspaceMemberResponse.from(
                workspaceService.inviteMember(
                        SecurityUtils.getCurrentMemberId(),
                        workspaceId,
                        request.email(),
                        request.role()
                )
        ));
    }

    @GetMapping("/{workspaceId}/members")
    public ApiResponse<List<WorkspaceDto.WorkspaceMemberResponse>> getWorkspaceMembers(
            @PathVariable Long workspaceId
    ) {
        List<WorkspaceDto.WorkspaceMemberResponse> members = workspaceService
                .getWorkspaceMembers(SecurityUtils.getCurrentMemberId(), workspaceId)
                .stream()
                .map(WorkspaceDto.WorkspaceMemberResponse::from)
                .toList();
        return ApiResponse.ok(members);
    }

    @DeleteMapping("/{workspaceId}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveWorkspace(@PathVariable Long workspaceId) {
        workspaceService.leaveWorkspace(SecurityUtils.getCurrentMemberId(), workspaceId);
    }

    public record CreateWorkspaceRequest(
            @NotBlank String name,
            String iconUrl
    ) {
    }

    public record InviteMemberRequest(
            @NotBlank String email,
            String role
    ) {
    }
}
