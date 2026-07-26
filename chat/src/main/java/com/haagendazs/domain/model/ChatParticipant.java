package com.haagendazs.domain.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

import lombok.*;

@Entity
@Table(
        name = "chat_participant",
        uniqueConstraints = @UniqueConstraint(columnNames = {"channel_id", "member_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long channelId;

    @Column(nullable = false)
    private Long memberId;

    private Long lastReadMessageId;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    @Builder
    private ChatParticipant(Long channelId, Long memberId) {
        this.channelId = channelId;
        this.memberId = memberId;
        this.joinedAt = LocalDateTime.now();
    }

    public void updateLastReadMessage(Long messageId) {
        this.lastReadMessageId = messageId;
    }
}
