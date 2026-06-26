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
@Table("notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    private Long id;

    @Column("member_id")
    private Long memberId;

    @Column("event_id")
    private Long eventId;

    @Column("is_read")
    private boolean read;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    public static Notification create(Long memberId, Long eventId) {
        Notification notification = new Notification();
        notification.memberId = memberId;
        notification.eventId = eventId;
        notification.read = false;
        return notification;
    }

    public static Notification withId(Long id, Long memberId, Long eventId) {
        Notification notification = create(memberId, eventId);
        notification.id = id;
        return notification;
    }

    public void markRead() {
        this.read = true;
    }

    public boolean isAlreadyRead() {
        return this.read;
    }
}
