package com.haagendazs.application.service;

import com.haagendazs.application.dto.ScheduledMessageCreateRequest;
import com.haagendazs.application.dto.ScheduledMessageResponse;
import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.domain.exception.ChatErrorCode;
import com.haagendazs.domain.model.ScheduledMessage;
import com.haagendazs.domain.model.ScheduledMessageStatus;
import com.haagendazs.domain.repository.ChatParticipantRepository;
import com.haagendazs.domain.repository.ScheduledMessageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledMessageServiceTest {

    @Mock
    private ScheduledMessageRepository scheduledMessageRepository;

    @Mock
    private ChatParticipantRepository chatParticipantRepository;

    @InjectMocks
    private ScheduledMessageService scheduledMessageService;

    private ScheduledMessage createScheduledMessage(Long channelId, Long senderId) {
        return ScheduledMessage.builder()
                .channelId(channelId)
                .senderId(senderId)
                .content("예약 메시지")
                .scheduledAt(LocalDateTime.now().plusHours(1))
                .build();
    }

    @Nested
    @DisplayName("예약 메시지 등록 (create)")
    class Create {

        @Test
        @DisplayName("참여자가 미래 시각으로 등록하면 성공한다")
        void create_success() {
            // given
            Long channelId = 1L, senderId = 1L;
            LocalDateTime future = LocalDateTime.now().plusHours(1);
            ScheduledMessageCreateRequest request =
                    new ScheduledMessageCreateRequest("예약 메시지", future);

            when(chatParticipantRepository.existsByChannelIdAndMemberId(channelId, senderId))
                    .thenReturn(true);

            ScheduledMessage saved = createScheduledMessage(channelId, senderId);
            when(scheduledMessageRepository.save(any(ScheduledMessage.class))).thenReturn(saved);

            // when
            ScheduledMessageResponse response =
                    scheduledMessageService.create(channelId, senderId, request);

            // then
            assertThat(response.content()).isEqualTo("예약 메시지");
            assertThat(response.status()).isEqualTo(ScheduledMessageStatus.PENDING);
            verify(scheduledMessageRepository).save(any(ScheduledMessage.class));
        }

        @Test
        @DisplayName("참여자가 아니면 NOT_A_ROOM_MEMBER 예외가 발생한다")
        void create_notParticipant() {
            // given
            Long channelId = 1L, senderId = 999L;
            ScheduledMessageCreateRequest request =
                    new ScheduledMessageCreateRequest("예약 메시지", LocalDateTime.now().plusHours(1));

            when(chatParticipantRepository.existsByChannelIdAndMemberId(channelId, senderId))
                    .thenReturn(false);

            // when & then
            assertThatThrownBy(() -> scheduledMessageService.create(channelId, senderId, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ChatErrorCode.NOT_A_ROOM_MEMBER);

            verify(scheduledMessageRepository, never()).save(any(ScheduledMessage.class));
        }

        @Test
        @DisplayName("과거 시각으로 등록하면 INVALID_SCHEDULED_TIME 예외가 발생한다")
        void create_pastTime() {
            // given
            Long channelId = 1L, senderId = 1L;
            LocalDateTime past = LocalDateTime.now().minusHours(1);
            ScheduledMessageCreateRequest request =
                    new ScheduledMessageCreateRequest("예약 메시지", past);

            when(chatParticipantRepository.existsByChannelIdAndMemberId(channelId, senderId))
                    .thenReturn(true);

            // when & then
            assertThatThrownBy(() -> scheduledMessageService.create(channelId, senderId, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ChatErrorCode.INVALID_SCHEDULED_TIME);

            verify(scheduledMessageRepository, never()).save(any(ScheduledMessage.class));
        }
    }

    @Nested
    @DisplayName("예약 메시지 목록 조회 (getPendingMessagesList)")
    class GetPendingList {

        @Test
        @DisplayName("PENDING 상태 예약 메시지 목록을 반환한다")
        void getPendingList_success() {
            // given
            Long channelId = 1L, senderId = 1L;
            when(scheduledMessageRepository.findAllByChannelIdAndSenderIdAndStatus(
                    channelId, senderId, ScheduledMessageStatus.PENDING))
                    .thenReturn(List.of(
                            createScheduledMessage(channelId, senderId),
                            createScheduledMessage(channelId, senderId)
                    ));

            // when
            List<ScheduledMessageResponse> result =
                    scheduledMessageService.getPendingMessagesList(channelId, senderId);

            // then
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(r -> r.status() == ScheduledMessageStatus.PENDING);
        }

        @Test
        @DisplayName("예약 메시지가 없으면 빈 리스트를 반환한다")
        void getPendingList_empty() {
            // given
            when(scheduledMessageRepository.findAllByChannelIdAndSenderIdAndStatus(
                    anyLong(), anyLong(), eq(ScheduledMessageStatus.PENDING)))
                    .thenReturn(List.of());

            // when
            List<ScheduledMessageResponse> result =
                    scheduledMessageService.getPendingMessagesList(1L, 1L);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("예약 메시지 취소 (cancel)")
    class Cancel {

        @Test
        @DisplayName("존재하는 예약 메시지를 본인이 취소하면 CANCELLED로 바뀐다")
        void cancel_success() {
            // given
            Long scheduledMessageId = 10L, senderId = 1L;
            ScheduledMessage message = createScheduledMessage(1L, senderId);

            when(scheduledMessageRepository.findById(scheduledMessageId))
                    .thenReturn(Optional.of(message));

            // when
            scheduledMessageService.cancel(scheduledMessageId, senderId);

            // then
            assertThat(message.getStatus()).isEqualTo(ScheduledMessageStatus.CANCELLED);
        }

        @Test
        @DisplayName("예약 메시지가 존재하지 않으면 예외가 발생한다")
        void cancel_notFound() {
            // given
            when(scheduledMessageRepository.findById(anyLong()))
                    .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> scheduledMessageService.cancel(999L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ChatErrorCode.SCHEDULE_MESSAGE_NOT_FOUND);
        }
    }
}
