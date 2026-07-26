package com.haagendazs.application.service;

import com.haagendazs.application.dto.ChatMessageSendRequest;
import com.haagendazs.application.dto.ChatMessageSendResponse;
import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.domain.exception.ChatErrorCode;
import com.haagendazs.domain.model.ChatParticipant;
import com.haagendazs.domain.model.Message;
import com.haagendazs.domain.repository.ChatParticipantRepository;
import com.haagendazs.domain.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final MessageRepository messageRepository;
    private final ChatParticipantRepository chatParticipantRepository;

    // 메세지를 저장하는 서비스 코드
    @Transactional
    public ChatMessageSendResponse saveMessage(Long channelId, ChatMessageSendRequest request, Long senderId)
    {
        if(!isRoomParticipant(senderId, channelId)){
            throw new BusinessException(ChatErrorCode.NOT_A_ROOM_MEMBER);
        }
        Message message = Message.builder()
                .channelId(channelId)
                .requesterId(senderId)
                .content(request.message())
                .build();
        Message savedMessage = messageRepository.save(message);

        return new ChatMessageSendResponse(
                savedMessage.getMessageId(),
                savedMessage.getChannelId(),
                savedMessage.getSenderId(),
                savedMessage.getContent(),
                savedMessage.getCreatedAt(),
                request.sendAt()
        );
    }



    //메시지를 읽음처리하는 서비스코드 현재는 db에서 직접 처리하지만 후에 redis에 저장하고 사용한다면 성능 개선을 할 수 있음.
    @Transactional
    public void markAsRead(Long channelId, Long memberId, Long lastReadMessageId) {
        ChatParticipant participant = chatParticipantRepository
                .findByChannelIdAndMemberId(channelId, memberId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.NOT_A_ROOM_MEMBER));

        participant.updateLastReadMessage(lastReadMessageId);
    }
    @Transactional
    public void deleteMessage(Long channelId, Long messageId, Long memberId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.MESSAGE_NOT_FOUND));

        // 이미 삭제된 메시지는 재삭제 불가
        if (message.isDeleted()) {
            throw new BusinessException(ChatErrorCode.MESSAGE_NOT_FOUND);
        }

        // 메시지가 요청한 채널에 속하는지 검증
        if (!message.getChannelId().equals(channelId)) {
            throw new BusinessException(ChatErrorCode.MESSAGE_NOT_FOUND);
        }

        // 현재 채널 참여자인지 검증 (강퇴 등으로 나갔을 수 있음)
        if (!isRoomParticipant(memberId, channelId)) {
            throw new BusinessException(ChatErrorCode.NOT_A_ROOM_MEMBER);
        }

        // 본인이 보낸 메시지만 삭제 가능
        if (!message.getSenderId().equals(memberId)) {
            throw new BusinessException(ChatErrorCode.NOT_MESSAGE_OWNER);
        }

        message.delete();   // soft delete → deleted_at 세팅
    }

    @Transactional
    public ChatMessageSendResponse updateMessage(Long channelId, Long messageId, String newContent, Long memberId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.MESSAGE_NOT_FOUND));

        // 메시지가 요청한 채널에 속하는지
        if (!message.getChannelId().equals(channelId)) {
            throw new BusinessException(ChatErrorCode.MESSAGE_NOT_FOUND);
        }

        // 현재 채널 참여자인지
        if (!isRoomParticipant(memberId, channelId)) {
            throw new BusinessException(ChatErrorCode.NOT_A_ROOM_MEMBER);
        }

        // 소유자 검증 + 삭제여부 검증 + content 교체 (엔티티가 처리)
        message.updateContent(newContent, memberId);

        return new ChatMessageSendResponse(
                message.getMessageId(),
                message.getChannelId(),
                message.getSenderId(),
                message.getContent(),
                message.getCreatedAt(),
                null   // 수정에서는 sendAt 지연측정 x
        );
    }


    public boolean isRoomParticipant(Long memberId, Long channelId){

        return chatParticipantRepository.existsByChannelIdAndMemberId(channelId, memberId);
    }


}
