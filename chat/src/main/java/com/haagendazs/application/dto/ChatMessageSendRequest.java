package com.haagendazs.application.dto;

import java.time.LocalDateTime;

public record ChatMessageSendRequest(
        String message,
        LocalDateTime sendAt   // k6 성능테스트 - 클라이언트가 보낸 시각
) {}
