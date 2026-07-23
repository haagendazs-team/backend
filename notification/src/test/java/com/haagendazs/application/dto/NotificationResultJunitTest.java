package com.haagendazs.application.dto;

import com.haagendazs.domain.model.Event;
import com.haagendazs.domain.model.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationResultJunitTest {

    @Test
    @DisplayName("Notification과 Event로 NotificationResult를 생성한다")
    void of_createsResultFromNotificationAndEvent() {
        Notification notification = Notification.withId(1L, 10L, 100L);
        Event event = Event.withId(100L, "PAYMENT_COMPLETED", "{\"amount\":1000}");

        NotificationResult result = NotificationResult.of(notification, event);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.eventTypeCode()).isEqualTo("PAYMENT_COMPLETED");
        assertThat(result.payload()).isEqualTo("{\"amount\":1000}");
        assertThat(result.read()).isFalse();
        assertThat(result.createdAt()).isNull();
    }
}
