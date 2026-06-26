package com.haagendazs.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryJunitTest {

    @Test
    @DisplayName("History.sent 시 status=SENT, isFailed=false")
    void sent_statusIsSent() {
        History history = History.sent(1L, ChannelType.EMAIL);

        assertThat(history.getNotificationId()).isEqualTo(1L);
        assertThat(history.getChannelType()).isEqualTo(ChannelType.EMAIL);
        assertThat(history.getStatus()).isEqualTo(SendStatus.SENT);
        assertThat(history.isFailed()).isFalse();
    }

    @Test
    @DisplayName("History.failed 시 status=FAILED, isFailed=true, errorMessage 설정됨")
    void failed_statusIsFailed() {
        History history = History.failed(1L, ChannelType.SLACK, "timeout");

        assertThat(history.getNotificationId()).isEqualTo(1L);
        assertThat(history.getChannelType()).isEqualTo(ChannelType.SLACK);
        assertThat(history.getStatus()).isEqualTo(SendStatus.FAILED);
        assertThat(history.isFailed()).isTrue();
        assertThat(history.getErrorMessage()).isEqualTo("timeout");
    }
}
