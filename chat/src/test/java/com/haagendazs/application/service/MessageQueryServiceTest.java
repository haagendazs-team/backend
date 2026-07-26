package com.haagendazs.application.service;

import com.haagendazs.application.dto.MessageHistoryResponse;
import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.domain.exception.ChatErrorCode;
import com.haagendazs.domain.model.Message;
import com.haagendazs.domain.repository.ChatParticipantRepository;
import com.haagendazs.domain.repository.MessageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageQueryServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ChatParticipantRepository chatParticipantRepository;

    @InjectMocks
    private MessageQueryService messageQueryService;

    private Message createMessage(Long channelId, Long senderId, String content) {
        return Message.builder()
                .channelId(channelId)
                .requesterId(senderId)
                .content(content)
                .build();
    }

    @Test
    @DisplayName("참여자가 아니면 NOT_A_ROOM_MEMBER 예외가 발생합니다.")
    void notParticipant() {
        // given
        when(chatParticipantRepository.existsByChannelIdAndMemberId(anyLong(), anyLong()))
                .thenReturn(false);

        // when & then
        assertThatThrownBy(() -> messageQueryService.getMessageHistory(1L, 999L, null, 30))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.NOT_A_ROOM_MEMBER);

        // 조회 메서드는 호출되지 않아야 함
        verify(messageRepository, never())
                .findAllByChannelIdOrderByMessageIdDesc(anyLong(), any(Pageable.class));
    }

    @Test
    @DisplayName("cursor가 없으면 최신부터 조회하는 메서드를 호출합니다")
    void firstPage_noCursor() {
        // given
        Long channelId = 1L, memberId = 1L;
        when(chatParticipantRepository.existsByChannelIdAndMemberId(channelId, memberId))
                .thenReturn(true);
        when(messageRepository.findAllByChannelIdOrderByMessageIdDesc(eq(channelId), any(Pageable.class)))
                .thenReturn(List.of(
                        createMessage(channelId, 1L, "메시지2"),
                        createMessage(channelId, 1L, "메시지1")
                ));

        // when
        List<MessageHistoryResponse> result =
                messageQueryService.getMessageHistory(channelId, memberId, null, 30);

        // then
        assertThat(result).hasSize(2);
        // cursor 없는 메서드가 호출되고, cursor 있는 메서드는 호출 안 됨
        verify(messageRepository).findAllByChannelIdOrderByMessageIdDesc(eq(channelId), any(Pageable.class));
        verify(messageRepository, never())
                .findAllByChannelIdAndMessageIdLessThanOrderByMessageIdDesc(anyLong(), anyLong(), any(Pageable.class));
    }

    @Test
    @DisplayName("cursor가 있으면 그 이전 메시지를 조회하는 메서드를 호출합니다.")
    void nextPage_withCursor() {
        // given
        Long channelId = 1L, memberId = 1L, cursor = 50L;
        when(chatParticipantRepository.existsByChannelIdAndMemberId(channelId, memberId))
                .thenReturn(true);
        when(messageRepository.findAllByChannelIdAndMessageIdLessThanOrderByMessageIdDesc(
                eq(channelId), eq(cursor), any(Pageable.class)))
                .thenReturn(List.of(createMessage(channelId, 1L, "이전 메시지")));

        // when
        List<MessageHistoryResponse> result =
                messageQueryService.getMessageHistory(channelId, memberId, cursor, 30);

        // then
        assertThat(result).hasSize(1);
        // cursor 있는 메서드가 호출되고, cursor 없는 메서드는 호출 안 됨
        verify(messageRepository)
                .findAllByChannelIdAndMessageIdLessThanOrderByMessageIdDesc(eq(channelId), eq(cursor), any(Pageable.class));
        verify(messageRepository, never())
                .findAllByChannelIdOrderByMessageIdDesc(anyLong(), any(Pageable.class));
    }

    @Test
    @DisplayName("조회 결과가 없으면 빈 리스트를 반환합니다")
    void emptyResult() {
        // given
        Long channelId = 1L, memberId = 1L;
        when(chatParticipantRepository.existsByChannelIdAndMemberId(channelId, memberId))
                .thenReturn(true);
        when(messageRepository.findAllByChannelIdOrderByMessageIdDesc(eq(channelId), any(Pageable.class)))
                .thenReturn(List.of());

        // when
        List<MessageHistoryResponse> result =
                messageQueryService.getMessageHistory(channelId, memberId, null, 30);

        // then
        assertThat(result).isEmpty();
    }
}
