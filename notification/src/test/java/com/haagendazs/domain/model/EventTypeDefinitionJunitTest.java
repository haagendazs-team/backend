package com.haagendazs.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EventTypeDefinitionJunitTest {

    @Test
    @DisplayName("EventTypeDefinition 생성 시 isNew()=true 반환")
    void isNew_returnsTrue_whenCreatedViaFactory() {
        EventTypeDefinition def = EventTypeDefinition.of("TEST_EVENT", false, true);

        assertThat(def.isNew()).isTrue();
        assertThat(def.getCode()).isEqualTo("TEST_EVENT");
        assertThat(def.isSingleTarget()).isTrue();
        assertThat(def.isScheduled()).isFalse();
        assertThat(def.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("스케줄 이벤트 타입은 scheduled=true 보유")
    void scheduledEventType_hasScheduledTrue() {
        EventTypeDefinition def = EventTypeDefinition.of("GAME_START", true, false);

        assertThat(def.isScheduled()).isTrue();
        assertThat(def.isSingleTarget()).isFalse();
    }
}
