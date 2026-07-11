package com.haagendazs.presentation;

import com.haagendazs.application.dto.MarkReadRequest;
import com.haagendazs.application.dto.MessageHistoryResponse;
import com.haagendazs.application.service.ChatService;
import com.haagendazs.application.service.MessageQueryService;
import com.haagendazs.common.response.ApiResponse;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class MessageController {

    private final MessageQueryService messageQueryService;
    private final ChatService chatService;
// 메세지 조회 컨트롤러
    @GetMapping("/channels/{channelId}/messages")
    public ApiResponse<List<MessageHistoryResponse>> getMessageHistory(
            @PathVariable Long channelId,
            @RequestParam(required = false) Long cursor,   // 없으면 최신부터
            @RequestParam(defaultValue = "30") int size,
            @RequestHeader("X-Member-Id") Long memberId    // 임시: JWT 완성 전까지 헤더로
    ) {
        List<MessageHistoryResponse> history = messageQueryService.getMessageHistory(
                channelId, memberId, cursor, size
        );
        return ApiResponse.ok(history);
    }

//메시지 읽음처리 컨트롤러
    @PatchMapping("/channels/{channelId}/read")
    public ApiResponse<Void> markAsRead(
            @PathVariable Long channelId,
            @RequestBody MarkReadRequest request,
            @RequestHeader("X-Member-Id") Long memberId
    ) {
        chatService.markAsRead(channelId, memberId, request.lastReadMessageId());
        return ApiResponse.ok();
    }


//메시지 삭제 컨트롤러
@DeleteMapping("/channels/{channelId}/messages/{messageId}")
public ApiResponse<Void> deleteMessage(
        @PathVariable Long channelId,
        @PathVariable Long messageId,
        @RequestHeader("X-Member-Id") Long memberId
) {
    chatService.deleteMessage(channelId, messageId, memberId);
    return ApiResponse.ok();
}
}
