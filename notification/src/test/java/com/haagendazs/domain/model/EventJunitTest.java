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
}
