package com.haagendazs.application.service;

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

    @Transactional
    public ChatMessageSendResponse saveMessage(Long channelId, ChatMessageSendRequest request, Long senderId)
    {
        if(!isRoomParticipant(senderId, channelId)){
            throw new BusinessException(ChatErrorCode.NOT_A_ROOM_MEMBER);
        }
        Message message = Message.builder()
                .channelId(channelId)
                .senderId(senderId)
                .content(request.message())
                .build();
        Message savedMessage = messageRepository.save(message);

        return new ChatMessageSendResponse(
                savedMessage.getMessageId(),
                savedMessage.getChannelId(),
                savedMessage.getSenderId(),
                savedMessage.getContent(),
                savedMessage.getCreateAt()
        );
    }

    public boolean isRoomParticipant(Long memberId, Long channelId){
        return chatParticipantRepository.existsByChannelIdAndMemberId(channelId, memberId);
    }

}
