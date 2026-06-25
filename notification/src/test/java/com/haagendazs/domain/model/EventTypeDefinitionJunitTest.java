package com.haagendazs.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EventTypeDefinitionJunitTest {

    @Test
    @DisplayName("EventTypeDefinition 생성 시 isNew()=true 반환")
    void isNew_returnsTrue_whenCreatedViaFactory() {
        EventTypeDefinition def = EventTypeDefinition.of(
                "TEST_EVENT", "notif:stream:test.event",
                false, true, "memberId", null, 0);

        assertThat(def.isNew()).isTrue();
        assertThat(def.getCode()).isEqualTo("TEST_EVENT");
        assertThat(def.getStreamKey()).isEqualTo("notif:stream:test.event");
        assertThat(def.isSingleTarget()).isTrue();
        assertThat(def.isScheduled()).isFalse();
        assertThat(def.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("스케줄 이벤트 타입은 scheduledAtField와 offset 보유")
    void scheduledEventType_hasScheduledAtField() {
        EventTypeDefinition def = EventTypeDefinition.of(
                "GAME_START", "notif:stream:game.starting",
                true, false, "memberId", "gameStartAt", 30);

        assertThat(def.isScheduled()).isTrue();
        assertThat(def.getScheduledAtField()).isEqualTo("gameStartAt");
        assertThat(def.getScheduledOffsetMinutes()).isEqualTo(30);
    }
}
