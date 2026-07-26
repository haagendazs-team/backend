package com.haagendazs.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.application.dto.ChatMessageSendResponse;
import com.haagendazs.application.dto.MarkReadRequest;
import com.haagendazs.application.dto.MessageHistoryResponse;
import com.haagendazs.application.dto.MessageUpdateRequest;
import com.haagendazs.application.service.ChatService;
import com.haagendazs.application.service.MessageQueryService;
import com.haagendazs.common.response.ApiResponse;

import com.haagendazs.infrastructure.redis.RedisPubSubService;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class MessageController {

    private final MessageQueryService messageQueryService;
    private final ChatService chatService;
    private final RedisPubSubService redisPubSubService;
    private final ObjectMapper objectMapper;

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

//메시지 수정 컨트롤러
    @PatchMapping("/channels/{channelId}/messages/{messageId}")
    public ApiResponse<Void> updateMessage(
            @PathVariable Long channelId,
            @PathVariable Long messageId,
            @RequestBody MessageUpdateRequest request,
            @RequestHeader("X-Member-Id") Long memberId
    ) {
        ChatMessageSendResponse response = chatService.updateMessage(channelId, messageId, request.content(), memberId);

        try {
            String json = objectMapper.writeValueAsString(response);
            redisPubSubService.publish("chat:" + channelId, json);
        } catch (Exception e) {
            // 로그만, 수정이 성공되었을 때, 굳이 에러를 발생시킬 필요가 없음. 테스트 후 추가 예정
        }
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
