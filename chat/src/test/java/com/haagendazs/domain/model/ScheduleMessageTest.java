package com.haagendazs.domain.model;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.domain.exception.ChatErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class ScheduledMessageTest {

    private ScheduledMessage createScheduledMessage(Long senderId) {
        return ScheduledMessage.builder()
                .channelId(1L)
                .senderId(senderId)
                .content("예약 메시지")
                .scheduledAt(LocalDateTime.now().plusHours(1))
                .build();
    }

    @Test
    @DisplayName("생성 후 메시지의 상태는 PENDING입니다.")
    void initialStatusIsPending() {
        // given & when
        ScheduledMessage sm = createScheduledMessage(1L);

        // then
        assertThat(sm.getStatus()).isEqualTo(ScheduledMessageStatus.PENDING);
    }

    @Test
    @DisplayName("markAsSent를 호출하면 상태가 SENT로 바뀝니다.")
    void markAsSent() {
        // given
        ScheduledMessage sm = createScheduledMessage(1L);

        // when
        sm.markAsSent();

        // then
        assertThat(sm.getStatus()).isEqualTo(ScheduledMessageStatus.SENT);
    }

    @Nested
    @DisplayName("예약 메시지 취소 (cancelBy)")
    class CancelBy {

        @Test
        @DisplayName("본인이 PENDING 상태를 취소하면 CANCELLED로 바뀝니다.")
        void cancelBy_success() {
            // given
            ScheduledMessage sm = createScheduledMessage(1L);

            // when
            sm.cancelBy(1L);

            // then
            assertThat(sm.getStatus()).isEqualTo(ScheduledMessageStatus.CANCELLED);
        }

        @Test
        @DisplayName("본인이 아니면 예외가 발생합니다.")
        void cancelBy_notOwner() {
            // given
            ScheduledMessage sm = createScheduledMessage(1L);

            // when & then
            assertThatThrownBy(() -> sm.cancelBy(999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ChatErrorCode.NOT_MESSAGE_OWNER);
        }

        @Test
        @DisplayName("이미 발송된(SENT) 메시지는 취소할 수 없습니다.")
        void cancelBy_alreadySent() {
            // given
            ScheduledMessage sm = createScheduledMessage(1L);
            sm.markAsSent();

            // when & then
            assertThatThrownBy(() -> sm.cancelBy(1L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("이미 취소된 메시지는 다시 취소할 수 없습니다")
        void cancelBy_alreadyCancelled() {
            // given
            ScheduledMessage sm = createScheduledMessage(1L);
            sm.cancelBy(1L);

            // when & then
            assertThatThrownBy(() -> sm.cancelBy(1L))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
