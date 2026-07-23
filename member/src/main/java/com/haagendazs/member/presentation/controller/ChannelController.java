package com.haagendazs.member.presentation.controller;

import com.haagendazs.common.response.ApiResponse;
import com.haagendazs.member.application.service.ChannelService;
import com.haagendazs.member.presentation.dto.ChannelDto;
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
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;

    @GetMapping("/workspaces/{workspaceId}/channels")
    public ApiResponse<List<ChannelDto.ChannelResponse>> getMyChannelsInWorkspace(
            @PathVariable Long workspaceId
    ) {
        List<ChannelDto.ChannelResponse> channels = channelService
                .getMyChannelsInWorkspace(SecurityUtils.getCurrentMemberId(), workspaceId)
                .stream()
                .map(ChannelDto.ChannelResponse::from)
                .toList();
        return ApiResponse.ok(channels);
    }

    @PostMapping("/workspaces/{workspaceId}/channels")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChannelDto.ChannelResponse> createChannel(
            @PathVariable Long workspaceId,
            @Valid @RequestBody CreateChannelRequest request
    ) {
        return ApiResponse.ok(ChannelDto.ChannelResponse.from(
                channelService.createChannel(
                        SecurityUtils.getCurrentMemberId(),
                        workspaceId,
                        request.name()
                )
        ));
    }

    @GetMapping("/workspaces/{workspaceId}/dms/{targetMemberId}")
    public ApiResponse<ChannelDto.ChannelResponse> getDmChannel(
            @PathVariable Long workspaceId,
            @PathVariable Long targetMemberId
    ) {
        return ApiResponse.ok(ChannelDto.ChannelResponse.from(
                channelService.getOrCreateDmChannel(
                        SecurityUtils.getCurrentMemberId(),
                        workspaceId,
                        targetMemberId
                )
        ));
    }

    @GetMapping("/channels/{channelId}")
    public ApiResponse<ChannelDto.ChannelResponse> getChannel(@PathVariable Long channelId) {
        return ApiResponse.ok(ChannelDto.ChannelResponse.from(
                channelService.getChannel(SecurityUtils.getCurrentMemberId(), channelId)
        ));
    }

    @PatchMapping("/channels/{channelId}")
    public ApiResponse<ChannelDto.ChannelResponse> updateChannel(
            @PathVariable Long channelId,
            @Valid @RequestBody ChannelDto.UpdateChannelRequest request
    ) {
        return ApiResponse.ok(ChannelDto.ChannelResponse.from(
                channelService.updateChannel(
                        SecurityUtils.getCurrentMemberId(),
                        channelId,
                        request.name()
                )
        ));
    }

    @DeleteMapping("/channels/{channelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChannel(@PathVariable Long channelId) {
        channelService.deleteChannel(SecurityUtils.getCurrentMemberId(), channelId);
    }

    @GetMapping("/channels/{channelId}/members")
    public ApiResponse<List<ChannelDto.ChannelMemberResponse>> getChannelMembers(
            @PathVariable Long channelId
    ) {
        List<ChannelDto.ChannelMemberResponse> members = channelService
                .getChannelMembers(SecurityUtils.getCurrentMemberId(), channelId)
                .stream()
                .map(ChannelDto.ChannelMemberResponse::from)
                .toList();
        return ApiResponse.ok(members);
    }

    @DeleteMapping("/channels/{channelId}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveChannel(@PathVariable Long channelId) {
        channelService.leaveChannel(SecurityUtils.getCurrentMemberId(), channelId);
    }

    public record CreateChannelRequest(
            @NotBlank String name
    ) {
    }
}
