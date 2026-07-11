package com.haagendazs.domain.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "scheduled_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduledMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long scheduledMessageId;
    @Column(nullable = false)
    private Long channelId;

    @Column(nullable = false)
    private Long senderId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduledMessageStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private ScheduledMessage(Long channelId, Long senderId, String content, LocalDateTime scheduledAt) {
        this.channelId = channelId;
        this.senderId = senderId;
        this.content = content;
        this.scheduledAt = scheduledAt;
        this.status = ScheduledMessageStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void markAsSent() {
        this.status = ScheduledMessageStatus.SENT;
    }

    public void cancel() {
        this.status = ScheduledMessageStatus.CANCELLED;
    }

}
