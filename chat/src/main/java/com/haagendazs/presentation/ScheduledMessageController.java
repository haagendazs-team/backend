package com.haagendazs.presentation;

import com.haagendazs.application.dto.ScheduledMessageCreateRequest;
import com.haagendazs.application.dto.ScheduledMessageResponse;
import com.haagendazs.application.service.ScheduledMessageService;
import com.haagendazs.common.response.ApiResponse;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ScheduledMessageController {
    private final ScheduledMessageService scheduledMessageService;

    @PostMapping("/channels/{channelId}/scheduled-messages")
    public ApiResponse<ScheduledMessageResponse> create(
            @PathVariable Long channelId,
            @RequestBody ScheduledMessageCreateRequest request,
            @RequestHeader("X-Member-Id") Long memberId
    ) {
        return ApiResponse.ok(scheduledMessageService.create(channelId, memberId, request));
    }

    @GetMapping("/channels/{channelId}/scheduled-messages")
    public ApiResponse<List<ScheduledMessageResponse>> getPendingList(
            @PathVariable Long channelId,
            @RequestHeader("X-Member-Id") Long memberId
    ) {
        return ApiResponse.ok(scheduledMessageService.getPendingMessagesList(channelId, memberId));
    }

    @DeleteMapping("/scheduled-messages/{scheduledMessageId}")
    public ApiResponse<Void> cancel(
            @PathVariable Long scheduledMessageId,
            @RequestHeader("X-Member-Id") Long memberId
    ) {
        scheduledMessageService.cancel(scheduledMessageId, memberId);
        return ApiResponse.ok();
    }
}
