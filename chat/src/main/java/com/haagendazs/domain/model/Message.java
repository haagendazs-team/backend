package com.haagendazs.domain.model;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.domain.exception.ChatErrorCode;
import jakarta.persistence.*;

import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // Build를 통해서만 사용가능하도록 설정
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long messageId;

    @Column(nullable = false)
    private Long channelId;

    @Column(nullable = false)
    private Long senderId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updateAt;

    private LocalDateTime deletedAt;


    @Builder
    private Message(Long channelId, Long requesterId, String content){
        this.channelId = channelId;
        this.senderId = requesterId;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    public void updateContent(String newContent, Long requesterId){
        if(!this.senderId.equals(requesterId)){
            throw new BusinessException(ChatErrorCode.NOT_MESSAGE_OWNER);
        }
        if(isDeleted()){
            throw new BusinessException(ChatErrorCode.MESSAGE_NOT_FOUND);
        }
        this.content = newContent;
        this.updateAt = LocalDateTime.now();
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
    public boolean isUpdated() {
        return this.updateAt != null;
    }
}
