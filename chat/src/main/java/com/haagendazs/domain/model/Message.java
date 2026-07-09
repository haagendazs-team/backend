package com.haagendazs.domain.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "message")
@Getter
@NoArgsConstructor
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long messageId;

    @Column(nullable = false)
    private Long channelId;

    @Column(nullable = false)
    private Long senderId;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private LocalDateTime createAt;

    private LocalDateTime deletedAt;

    @Builder
    private Message(Long channelId, Long senderId, String content){
        this.channelId = channelId;
        this.senderId = senderId;
        this.content = content;
        this.createAt = LocalDateTime.now();
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
