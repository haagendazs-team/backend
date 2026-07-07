package com.haagendazs.domain.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "chat_participant",
        uniqueConstraints = @UniqueConstraint(columnNames = {"channel_id", "member_id"})
)
@Getter
@NoArgsConstructor
public class ChatParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long channelId;

    @Column(nullable = false)
    private Long memberId;

    private Long lastMessageId;

    @Column(nullable = false)
    private LocalDateTime joinAt;

    @Builder
    private ChatParticipant(Long channelId, Long memberId){
        this.channelId = channelId;
        this.memberId = memberId;
        this.joinAt = LocalDateTime.now();
    }

    public void updateLastMessageId(Long messageId){
        this.lastMessageId = messageId;
    }
}
