package com.haagendazs.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

class EventJunitTest {

    @Test
    @DisplayName("Event 생성 시 eventTypeCode가 String으로 저장된다")
    void create_storesEventTypeCode_asString() {
        Event event = Event.create("TICKET_OPEN", "{\"memberId\":1}");

        assertThat(event.getEventTypeCode()).isEqualTo("TICKET_OPEN");
        assertThat(event.getTypeName()).isEqualTo("TICKET_OPEN");
        assertThat(event.getStatus()).isEqualTo(EventStatus.PENDING);
    }

    @Test
    @DisplayName("예약 이벤트 생성 시 scheduledAt이 설정된다")
    void createScheduled_setsScheduledAt() {
        LocalDateTime scheduledAt = LocalDateTime.now().plusHours(1);
        Event event = Event.createScheduled("GAME_START", "{}", scheduledAt);

        assertThat(event.isScheduled()).isTrue();
        assertThat(event.getEventTypeCode()).isEqualTo("GAME_START");
    }

    @Test
    @DisplayName("Event.withId 시 id가 설정된다")
    void withId_setsId() {
        Event event = Event.withId(42L, "TICKET_OPEN", "{}");

        assertThat(event.getId()).isEqualTo(42L);
        assertThat(event.getEventTypeCode()).isEqualTo("TICKET_OPEN");
    }

    @Test
    @DisplayName("isScheduled — scheduledAt 없으면 false 반환")
    void isScheduled_withoutScheduledAt_returnsFalse() {
        Event event = Event.create("TICKET_OPEN", "{}");

        assertThat(event.isScheduled()).isFalse();
    }

    @Test
    @DisplayName("assignStreamMessageId 후 hasStreamMessageId=true 반환")
    void assignStreamMessageId_setsId() {
        Event event = Event.create("TICKET_OPEN", "{}");

        event.assignStreamMessageId("1234-0");

        assertThat(event.hasStreamMessageId()).isTrue();
        assertThat(event.getStreamMessageId()).isEqualTo("1234-0");
    }

    @Test
    @DisplayName("hasStreamMessageId — streamMessageId 없으면 false")
    void hasStreamMessageId_withoutId_returnsFalse() {
        Event event = Event.create("TICKET_OPEN", "{}");

        assertThat(event.hasStreamMessageId()).isFalse();
    }

    @Test
    @DisplayName("markPending/markProcessing/markPublished/markFailed/markPermanentlyFailed/markCancelled — 상태 전이 확인")
    void statusTransitions_allStatusesReachable() {
        Event event = Event.create("TICKET_OPEN", "{}");

        event.markProcessing();
        assertThat(event.getStatus()).isEqualTo(EventStatus.PROCESSING);

        event.markPublished();
        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);

        event.markFailed();
        assertThat(event.getStatus()).isEqualTo(EventStatus.FAILED);

        event.markPermanentlyFailed();
        assertThat(event.getStatus()).isEqualTo(EventStatus.PERMANENTLY_FAILED);

        event.markCancelled();
        assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);

        event.markPending();
        assertThat(event.getStatus()).isEqualTo(EventStatus.PENDING);
    }

    @Test
    @DisplayName("incrementRetry — retryCount가 1 증가한다")
    void incrementRetry_incrementsCount() {
        Event event = Event.create("TICKET_OPEN", "{}");

        event.incrementRetry();
        event.incrementRetry();

        assertThat(event.getRetryCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("incrementStuckRetry — stuckRetryCount가 1 증가한다")
    void incrementStuckRetry_incrementsCount() {
        Event event = Event.create("TICKET_OPEN", "{}");

        event.incrementStuckRetry();

        assertThat(event.getStuckRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("incrementRetryAndCheckExhausted — backoffSize 도달 시 true 반환")
    void incrementRetryAndCheckExhausted_atBackoffSize_returnsTrue() {
        Event event = Event.create("TICKET_OPEN", "{}");

        boolean notExhausted = event.incrementRetryAndCheckExhausted(3);
        assertThat(notExhausted).isFalse();
        boolean notExhausted2 = event.incrementRetryAndCheckExhausted(3);
        assertThat(notExhausted2).isFalse();
        boolean exhausted = event.incrementRetryAndCheckExhausted(3);
        assertThat(exhausted).isTrue();
    }
}
