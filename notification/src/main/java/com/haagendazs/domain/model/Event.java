package com.haagendazs.domain.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Getter
@Table("events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event {

    @Id
    private Long id;

    @Column("event_type")
    private String eventTypeCode;

    private String payload;

    private EventStatus status;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("published_at")
    private LocalDateTime publishedAt;

    @Column("scheduled_at")
    private LocalDateTime scheduledAt;

    @Column("stream_message_id")
    private String streamMessageId;

    @Column("retry_count")
    private int retryCount = 0;

    @Column("stuck_retry_count")
    private int stuckRetryCount = 0;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;

    public static Event create(String eventTypeCode, String payload) {
        Event event = new Event();
        event.eventTypeCode = eventTypeCode;
        event.payload = payload;
        event.status = EventStatus.PENDING;
        return event;
    }

    public static Event createScheduled(String eventTypeCode, String payload, LocalDateTime scheduledAt) {
        Event event = create(eventTypeCode, payload);
        event.scheduledAt = scheduledAt;
        return event;
    }

    public static Event withId(Long id, String eventTypeCode, String payload) {
        Event event = create(eventTypeCode, payload);
        event.id = id;
        return event;
    }

    public void assignStreamMessageId(String streamMessageId) {
        this.streamMessageId = streamMessageId;
    }

    public boolean isScheduled() {
        return scheduledAt != null;
    }

    public boolean hasStreamMessageId() {
        return streamMessageId != null;
    }

    public String getEventTypeCode() {
        return this.eventTypeCode;
    }

    public String getTypeName() {
        return getEventTypeCode();
    }

    public void markPending() {
        this.status = EventStatus.PENDING;
    }

    public void markProcessing() {
        this.status = EventStatus.PROCESSING;
    }

    public void markPublished() {
        this.status = EventStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = EventStatus.FAILED;
    }

    public void incrementRetry() {
        this.retryCount++;
    }

    public void incrementStuckRetry() {
        this.stuckRetryCount++;
    }

    public boolean incrementRetryAndCheckExhausted(int backoffSize) {
        this.retryCount++;
        return this.retryCount >= backoffSize;
    }

    public void markPermanentlyFailed() {
        this.status = EventStatus.PERMANENTLY_FAILED;
    }

    public void markCancelled() {
        this.status = EventStatus.CANCELLED;
    }
}
