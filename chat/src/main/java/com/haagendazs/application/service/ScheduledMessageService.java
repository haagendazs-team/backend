package com.haagendazs.application.service;

import com.haagendazs.application.dto.ScheduledMessageCreateRequest;
import com.haagendazs.application.dto.ScheduledMessageResponse;
import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.domain.exception.ChatErrorCode;
import com.haagendazs.domain.model.ScheduledMessage;
import com.haagendazs.domain.model.ScheduledMessageStatus;
import com.haagendazs.domain.repository.ChatParticipantRepository;
import com.haagendazs.domain.repository.ScheduledMessageRepository;

import java.time.LocalDateTime;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScheduledMessageService {
    private final ScheduledMessageRepository scheduledMessageRepository;
    private final ChatParticipantRepository chatParticipantRepository;

    @Transactional
    public ScheduledMessageResponse create(Long channelId, Long senderId, ScheduledMessageCreateRequest createRequest) {
        if(!chatParticipantRepository.existsByChannelIdAndMemberId(channelId, senderId)) {
            throw new BusinessException(ChatErrorCode.NOT_A_ROOM_MEMBER);
        }
        if(createRequest.scheduledAt().isBefore(LocalDateTime.now())){
            throw new BusinessException(ChatErrorCode.INVALID_SCHEDULED_TIME);
        }
        ScheduledMessage scheduledMessage = ScheduledMessage.builder()
                .channelId(channelId)
                .senderId(senderId)
                .content(createRequest.content())
                .scheduledAt(createRequest.scheduledAt())
                .build();

        ScheduledMessage createdMessageSaved = scheduledMessageRepository.save(scheduledMessage);
        return ScheduledMessageResponse.from(createdMessageSaved);
    }
    @Transactional(readOnly = true)
    public List<ScheduledMessageResponse> getPendingMessagesList(Long channelId, Long senderId) {
        return scheduledMessageRepository
                .findAllByChannelIdAndSenderIdAndStatus(channelId, senderId, ScheduledMessageStatus.PENDING)
                .stream() // 리스트를 컨베이어 벨트에 올려서 작업 준비
                .map(ScheduledMessageResponse::from)//상자를 까서 DTO로 변환
                .toList();//다시 내용물을 List형식으로 재포장
    }
    @Transactional
    public void cancel(Long scheduledMessageId, Long senderId) {
        ScheduledMessage message = scheduledMessageRepository.findById(scheduledMessageId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.SCHEDULE_MESSAGE_NOT_FOUND));

        message.cancelBy(senderId);
    }

}
