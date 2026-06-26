package com.haagendazs.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationJunitTest {

    @Test
    @DisplayName("Notification.create 시 is_read=false로 초기화된다")
    void create_initializedReadFalse() {
        Notification notification = Notification.create(1L, 10L);

        assertThat(notification.getMemberId()).isEqualTo(1L);
        assertThat(notification.getEventId()).isEqualTo(10L);
        assertThat(notification.isRead()).isFalse();
        assertThat(notification.isAlreadyRead()).isFalse();
    }

    @Test
    @DisplayName("markRead 호출 후 isAlreadyRead=true 반환된다")
    void markRead_setsReadTrue() {
        Notification notification = Notification.create(1L, 10L);

        notification.markRead();

        assertThat(notification.isAlreadyRead()).isTrue();
    }

    @Test
    @DisplayName("Notification.withId 시 id가 설정된다")
    void withId_setsId() {
        Notification notification = Notification.withId(5L, 1L, 10L);

        assertThat(notification.getId()).isEqualTo(5L);
        assertThat(notification.getMemberId()).isEqualTo(1L);
        assertThat(notification.getEventId()).isEqualTo(10L);
    }
}
