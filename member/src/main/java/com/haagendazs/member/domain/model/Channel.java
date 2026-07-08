package com.haagendazs.member.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "channel")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "channel_id")
    private Long channelId;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "is_direct_message", nullable = false)
    private boolean isDirectMessage;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static Channel create(Long workspaceId, String name, boolean isDirectMessage) {
        Channel channel = new Channel();
        channel.workspaceId = workspaceId;
        channel.name = name;
        channel.isDirectMessage = isDirectMessage;
        return channel;
    }

    public void updateName(String name) {
        if (name != null) {
            this.name = name;
        }
    }
}
