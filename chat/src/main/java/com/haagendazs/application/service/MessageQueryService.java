package com.haagendazs.application.service;

import com.haagendazs.application.dto.MessageHistoryResponse;
import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.domain.exception.ChatErrorCode;
import com.haagendazs.domain.model.Message;
import com.haagendazs.domain.repository.ChatParticipantRepository;
import com.haagendazs.domain.repository.MessageRepository;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 커서 방식을 사용하여 메시지를 조회하는 서비스 default로 30size를 잡고, 요청이 없을 시 30개씩 메시지를 가져오도록 함
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageQueryService {

    private final MessageRepository messageRepository;
    private final ChatParticipantRepository chatParticipantRepository;

    public List<MessageHistoryResponse> getMessageHistory(Long channelId, Long memberId, Long cursor,
            int size) {
        // 참여자 검증
        if (!chatParticipantRepository.existsByChannelIdAndMemberId(channelId, memberId)) {
            throw new BusinessException(ChatErrorCode.NOT_A_ROOM_MEMBER);
        }
        List<Message> messages = (cursor == null)
                ? messageRepository.findAllByChannelIdOrderByMessageIdDesc(
                channelId, PageRequest.of(0, size))
                : messageRepository.findAllByChannelIdAndMessageIdLessThanOrderByMessageIdDesc(
                channelId, cursor, PageRequest.of(0, size));

        return messages.stream()
                .map(MessageHistoryResponse::from)
                .toList();
    }

}
