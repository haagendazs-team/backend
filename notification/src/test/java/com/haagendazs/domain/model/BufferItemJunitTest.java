package com.haagendazs.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.RecordId;

import static org.assertj.core.api.Assertions.assertThat;

class BufferItemJunitTest {

    @Test
    @DisplayName("BufferItem.of 시 모든 필드가 올바르게 저장된다")
    void of_storesAllFields() {
        Notification notification = Notification.create(1L, 10L);
        RecordId recordId = RecordId.of("1234-0");

        BufferItem item = BufferItem.of(notification, "알림 제목", "{}", "notif:stream:ticket.open", recordId);

        assertThat(item.notification()).isEqualTo(notification);
        assertThat(item.subject()).isEqualTo("알림 제목");
        assertThat(item.payload()).isEqualTo("{}");
        assertThat(item.streamKey()).isEqualTo("notif:stream:ticket.open");
        assertThat(item.recordId()).isEqualTo(recordId);
    }
}
