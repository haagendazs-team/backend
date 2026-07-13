package com.haagendazs.application.service;

import com.haagendazs.application.dto.ChatMessageSendRequest;
import com.haagendazs.application.dto.ChatMessageSendResponse;
import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.domain.exception.ChatErrorCode;
import com.haagendazs.domain.model.ChatParticipant;
import com.haagendazs.domain.model.Message;
import com.haagendazs.domain.repository.ChatParticipantRepository;
import com.haagendazs.domain.repository.MessageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ChatParticipantRepository chatParticipantRepository;

    @InjectMocks
    private ChatService chatService;

    @Nested
    @DisplayName("메시지 저장 (saveMessage)")
    class SaveMessage {

        @Test
        @DisplayName("참여자가 보낸 메시지는 저장되고 응답을 반환합니다.")
        void saveMessage_success() {
            // given
            Long channelId = 1L;
            Long senderId = 1L;
            ChatMessageSendRequest request = new ChatMessageSendRequest("안녕하세요", null);

            // 참여자 검증 통과하도록 설정
            when(chatParticipantRepository.existsByChannelIdAndMemberId(channelId, senderId))
                    .thenReturn(true);

            // save가 호출되면 저장된 Message를 리턴함
            Message savedMessage = Message.builder()
                    .channelId(channelId)
                    .requesterId(senderId)
                    .content("안녕하세요")
                    .build();
            when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);

            // when
            ChatMessageSendResponse response = chatService.saveMessage(channelId, request, senderId);

            // then
            assertThat(response.content()).isEqualTo("안녕하세요");
            assertThat(response.channelId()).isEqualTo(channelId);
            assertThat(response.senderId()).isEqualTo(senderId);
            verify(messageRepository).save(any(Message.class));   // save가 실제로 호출됐는지 확인
        }

        @Test
        @DisplayName("참여자가 아니면 NOT_A_ROOM_MEMBER 예외가 발생합니다.")
        void saveMessage_notParticipant() {
            // given
            Long channelId = 1L;
            Long senderId = 999L;
            ChatMessageSendRequest request = new ChatMessageSendRequest("안녕하세요", null);

            // 참여자 검증 실패하도록 설정
            when(chatParticipantRepository.existsByChannelIdAndMemberId(channelId, senderId))
                    .thenReturn(false);

            // when & then
            assertThatThrownBy(() -> chatService.saveMessage(channelId, request, senderId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ChatErrorCode.NOT_A_ROOM_MEMBER);

            // 참여자가 아니면 save는 호출되지 않아야 함
            verify(messageRepository, never()).save(any(Message.class));
        }
    }

    @Nested
    @DisplayName("읽음 처리 (markAsRead)")
    class MarkAsRead {

        @Test
        @DisplayName("참여자의 last_read_message_id를 갱신합니다.")
        void markAsRead_success() {
            // given
            Long channelId = 1L;
            Long memberId = 1L;
            Long lastReadMessageId = 100L;

            ChatParticipant participant = ChatParticipant.builder()
                    .channelId(channelId)
                    .memberId(memberId)
                    .build();

            when(chatParticipantRepository.findByChannelIdAndMemberId(channelId, memberId))
                    .thenReturn(Optional.of(participant));

            // when
            chatService.markAsRead(channelId, memberId, lastReadMessageId);

            // then
            assertThat(participant.getLastReadMessageId()).isEqualTo(lastReadMessageId);
        }

        @Test
        @DisplayName("참여자가 없으면 NOT_A_ROOM_MEMBER 예외가 발생합니다.")
        void markAsRead_notParticipant() {
            // given
            when(chatParticipantRepository.findByChannelIdAndMemberId(anyLong(), anyLong()))
                    .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> chatService.markAsRead(1L, 999L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ChatErrorCode.NOT_A_ROOM_MEMBER);
        }
    }
    @Nested
    @DisplayName("메시지 삭제 (deleteMessage)")
    class DeleteMessage {

        private Message createMessage(Long channelId, Long senderId) {
            return Message.builder()
                    .channelId(channelId)
                    .requesterId(senderId)
                    .content("삭제 대상 메시지")
                    .build();
        }

        @Test
        @DisplayName("모든 검증을 통과하면 soft delete 됩니다.")
        void deleteMessage_success() {
            // given
            Long channelId = 1L, messageId = 10L, memberId = 1L;
            Message message = createMessage(channelId, memberId);

            when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
            when(chatParticipantRepository.existsByChannelIdAndMemberId(channelId, memberId))
                    .thenReturn(true);

            // when
            chatService.deleteMessage(channelId, messageId, memberId);

            // then
            assertThat(message.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("메시지가 존재하지 않으면 MESSAGE_NOT_FOUND")
        void deleteMessage_notFound() {
            // given
            when(messageRepository.findById(anyLong())).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> chatService.deleteMessage(1L, 999L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ChatErrorCode.MESSAGE_NOT_FOUND);
        }

        @Test
        @DisplayName("이미 삭제된 메시지면 MESSAGE_NOT_FOUND")
        void deleteMessage_alreadyDeleted() {
            // given
            Long channelId = 1L, messageId = 10L, memberId = 1L;
            Message message = createMessage(channelId, memberId);
            message.delete();   // 미리 삭제 상태로

            when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

            // when & then
            assertThatThrownBy(() -> chatService.deleteMessage(channelId, messageId, memberId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ChatErrorCode.MESSAGE_NOT_FOUND);
        }

        @Test
        @DisplayName("메시지의 채널이 요청 채널과 다르면 MESSAGE_NOT_FOUND")
        void deleteMessage_channelMismatch() {
            // given
            Long requestChannelId = 1L, messageId = 10L, memberId = 1L;
            Message message = createMessage(2L, memberId);   // 메시지는 채널 2 소속

            when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

            // when & then
            assertThatThrownBy(() -> chatService.deleteMessage(requestChannelId, messageId, memberId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ChatErrorCode.MESSAGE_NOT_FOUND);
        }

        @Test
        @DisplayName("현재 채널 참여자가 아니면 NOT_A_ROOM_MEMBER")
        void deleteMessage_notParticipant() {
            // given
            Long channelId = 1L, messageId = 10L, memberId = 1L;
            Message message = createMessage(channelId, memberId);

            when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
            when(chatParticipantRepository.existsByChannelIdAndMemberId(channelId, memberId))
                    .thenReturn(false);   // 참여자 아님

            // when & then
            assertThatThrownBy(() -> chatService.deleteMessage(channelId, messageId, memberId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ChatErrorCode.NOT_A_ROOM_MEMBER);
        }

        @Test
        @DisplayName("본인이 보낸 메시지가 아니면 NOT_MESSAGE_OWNER")
        void deleteMessage_notOwner() {
            // given
            Long channelId = 1L, messageId = 10L, memberId = 1L;
            Message message = createMessage(channelId, 999L);   // 메시지 소유자는 999

            when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
            when(chatParticipantRepository.existsByChannelIdAndMemberId(channelId, memberId))
                    .thenReturn(true);

            // when & then
            assertThatThrownBy(() -> chatService.deleteMessage(channelId, messageId, memberId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ChatErrorCode.NOT_MESSAGE_OWNER);
        }
    }

    @Nested
    @DisplayName("메시지 수정 (updateMessage)")
    class UpdateMessage {

        private Message createMessage(Long channelId, Long senderId) {
            return Message.builder()
                    .channelId(channelId)
                    .requesterId(senderId)
                    .content("원본 메시지")
                    .build();
        }

        @Test
        @DisplayName("모든 검증을 통과하면 content가 수정되고 응답을 반환합니다")
        void updateMessage_success() {
            // given
            Long channelId = 1L, messageId = 10L, memberId = 1L;
            Message message = createMessage(channelId, memberId);

            when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
            when(chatParticipantRepository.existsByChannelIdAndMemberId(channelId, memberId))
                    .thenReturn(true);

            // when
            ChatMessageSendResponse response =
                    chatService.updateMessage(channelId, messageId, "수정된 메시지", memberId);

            // then
            assertThat(response.content()).isEqualTo("수정된 메시지");
            assertThat(message.isUpdated()).isTrue();
        }

        @Test
        @DisplayName("메시지가 존재하지 않으면 MESSAGE_NOT_FOUND")
        void updateMessage_notFound() {
            // given
            when(messageRepository.findById(anyLong())).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> chatService.updateMessage(1L, 999L, "수정", 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ChatErrorCode.MESSAGE_NOT_FOUND);
        }

        @Test
        @DisplayName("메시지의 채널이 요청 채널과 다르면 MESSAGE_NOT_FOUND")
        void updateMessage_channelMismatch() {
            // given
            Long requestChannelId = 1L, messageId = 10L, memberId = 1L;
            Message message = createMessage(2L, memberId);

            when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

            // when & then
            assertThatThrownBy(() -> chatService.updateMessage(requestChannelId, messageId, "수정", memberId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ChatErrorCode.MESSAGE_NOT_FOUND);
        }

        @Test
        @DisplayName("현재 채널 참여자가 아니면 NOT_A_ROOM_MEMBER")
        void updateMessage_notParticipant() {
            // given
            Long channelId = 1L, messageId = 10L, memberId = 1L;
            Message message = createMessage(channelId, memberId);

            when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
            when(chatParticipantRepository.existsByChannelIdAndMemberId(channelId, memberId))
                    .thenReturn(false);

            // when & then
            assertThatThrownBy(() -> chatService.updateMessage(channelId, messageId, "수정", memberId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ChatErrorCode.NOT_A_ROOM_MEMBER);
        }

        @Test
        @DisplayName("본인 메시지가 아니면 NOT_MESSAGE_OWNER (엔티티 검증)")
        void updateMessage_notOwner() {
            // given
            Long channelId = 1L, messageId = 10L, memberId = 1L;
            Message message = createMessage(channelId, 999L);   // 소유자 999

            when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
            when(chatParticipantRepository.existsByChannelIdAndMemberId(channelId, memberId))
                    .thenReturn(true);

            // when & then
            assertThatThrownBy(() -> chatService.updateMessage(channelId, messageId, "수정", memberId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ChatErrorCode.NOT_MESSAGE_OWNER);
        }
    }
}

