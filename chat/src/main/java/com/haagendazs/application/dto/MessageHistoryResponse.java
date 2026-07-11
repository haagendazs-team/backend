package com.haagendazs.application.dto;


import com.haagendazs.domain.model.Message;

import java.time.LocalDateTime;

public record MessageHistoryResponse(
        Long messageId,
        Long channelId,
        Long senderId,
        String content,
        LocalDateTime createdAt,
        boolean deleted
) {
    //record 같은 경우 반환 타입과 from(메서드 명)을 꼭 명시해주어야한다.
    public static MessageHistoryResponse from(Message message){
        return new MessageHistoryResponse(
                message.getMessageId(),
                message.getChannelId(),
                message.getSenderId(),
                message.isDeleted() ? "삭제된 메시지입니다" : message.getContent(),
                message.getCreatedAt(),
                message.isDeleted()
        );
    }

}
