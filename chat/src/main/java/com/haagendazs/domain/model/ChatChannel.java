package com.haagendazs.domain.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_channel")
@Getter
@NoArgsConstructor
public class ChatChannel {

    @Id
    private Long channelId;

    private Long workspaceId;

    @Column(length = 100)
    private String channelName;

    @Column(nullable = false)
    private boolean isDirectMessage;

    @Column(length = 100, unique = true)
    private String dmKey;

    @Column(nullable = false)
    private LocalDateTime syncedAt;

    @Builder
    private ChatChannel(Long channelId, Long workspaceId, String channelName,
                        boolean isDirectMessage, String dmKey) {
        this.channelId = channelId;
        this.workspaceId = workspaceId;
        this.channelName = channelName;
        this.isDirectMessage = isDirectMessage;
        this.dmKey = dmKey;
        this.syncedAt = LocalDateTime.now();
    }

    public void updateChannel(String channelName) {
        this.channelName = channelName;
        this.syncedAt = LocalDateTime.now();
    }

}
