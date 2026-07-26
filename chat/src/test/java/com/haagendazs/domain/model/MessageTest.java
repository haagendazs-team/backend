package com.haagendazs.domain.model;


import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.domain.exception.ChatErrorCode;
import com.haagendazs.domain.model.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MessageTest {

    private Message createMessage(Long senderId) {
        return Message.builder()
                .channelId(1L)
                .requesterId(senderId)
                .content("원본 메시지")
                .build();
    }

    @Nested
    @DisplayName("메시지 수정")
    class UpdateContent {

        @Test
        @DisplayName("본인 메시지는 수정에 성공하고 updatedAt이 채워진다")
        void updateContent_success() {
            // given
            Message message = createMessage(1L);

            // when
            message.updateContent("수정된 메시지", 1L);

            // then
            assertThat(message.getContent()).isEqualTo("수정된 메시지");
            assertThat(message.isUpdated()).isTrue();
        }

        @Test
        @DisplayName("본인이 아니면 NOT_MESSAGE_OWNER 예외가 발생한다")
        void updateContent_notOwner() {
            // given
            Message message = createMessage(1L);

            // when & then
            assertThatThrownBy(() -> message.updateContent("수정 시도", 999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ChatErrorCode.NOT_MESSAGE_OWNER);
        }

        @Test
        @DisplayName("이미 삭제된 메시지는 수정할 수 없다")
        void updateContent_deleted() {
            // given
            Message message = createMessage(1L);
            message.delete();

            // when & then
            assertThatThrownBy(() -> message.updateContent("수정 시도", 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ChatErrorCode.MESSAGE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("메시지 삭제")
    class Delete {

        @Test
        @DisplayName("삭제하면 deletedAt이 채워지고 isDeleted가 true다")
        void delete_success() {
            // given
            Message message = createMessage(1L);

            // when
            message.delete();

            // then
            assertThat(message.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("삭제 전에는 isDeleted가 false다")
        void notDeleted() {
            Message message = createMessage(1L);
            assertThat(message.isDeleted()).isFalse();
        }
    }
}
