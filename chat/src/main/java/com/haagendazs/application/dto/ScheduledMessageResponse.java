package com.haagendazs.application.dto;

import com.haagendazs.domain.model.ScheduledMessage;
import com.haagendazs.domain.model.ScheduledMessageStatus;

import java.time.LocalDateTime;

public record ScheduledMessageResponse(
  Long scheduleMessageId,
  Long channelId,
  Long senderId,
  String content,
  LocalDateTime scheduledAt,
  ScheduledMessageStatus status
) {
    public static ScheduledMessageResponse from(ScheduledMessage scheduledMessage) {
        return new ScheduledMessageResponse(
                scheduledMessage.getScheduledMessageId(),
                scheduledMessage.getChannelId(),
                scheduledMessage.getSenderId(),
                scheduledMessage.getContent(),
                scheduledMessage.getScheduledAt(),
                scheduledMessage.getStatus()
        );
    }
}
