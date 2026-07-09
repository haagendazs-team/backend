package com.haagendazs.application.dto;

import java.time.LocalDateTime;

//Stomp Controller와 RedisPubSubService에서 사용하므로 application.dto에 생성
/*
    "messageId": 15,
    "channelId": 5,
    "senderId": 42,
    "content": "안녕하세요",
    "createdAt": "2026-07-09T14:30:00"
*/
public record ChatMessageSendResponse(
    Long messageId,
    Long channelId,
    Long senderId,
    String content,
    LocalDateTime createAt
) { }
