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

    @Test
    @DisplayName("메타데이터 포함 팩토리 메서드로 생성 시 displayName, description, category가 설정된다")
    void ofWithMetadata_setsDisplayFields() {
        EventTypeDefinition def = EventTypeDefinition.of(
                "PAYMENT_COMPLETED", false, true,
                "결제 완료 알림", "결제가 완료되면 알림을 받습니다.", "PAYMENT");

        assertThat(def.getDisplayName()).isEqualTo("결제 완료 알림");
        assertThat(def.getDescription()).isEqualTo("결제가 완료되면 알림을 받습니다.");
        assertThat(def.getCategory()).isEqualTo("PAYMENT");
        assertThat(def.isNew()).isTrue();
    }

    @Test
    @DisplayName("getId()는 code를 반환한다")
    void getId_returnsCode() {
        EventTypeDefinition def = EventTypeDefinition.of("PAYMENT_COMPLETED", false, true);

        assertThat(def.getId()).isEqualTo("PAYMENT_COMPLETED");
    }
}
