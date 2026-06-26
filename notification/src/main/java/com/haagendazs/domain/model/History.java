package com.haagendazs.domain.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Getter
@Table("history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class History {

    @Id
    private Long id;

    @Column("notification_id")
    private Long notificationId;

    @Column("channel_type")
    private ChannelType channelType;

    private SendStatus status;

    @Column("error_message")
    private String errorMessage;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    public static History sent(Long notificationId, ChannelType channelType) {
        History history = new History();
        history.notificationId = notificationId;
        history.channelType = channelType;
        history.status = SendStatus.SENT;
        return history;
    }

    public static History failed(Long notificationId, ChannelType channelType, String errorMessage) {
        History history = new History();
        history.notificationId = notificationId;
        history.channelType = channelType;
        history.status = SendStatus.FAILED;
        history.errorMessage = errorMessage;
        return history;
    }

    public boolean isFailed() {
        return status == SendStatus.FAILED;
    }
}
