package com.haagendazs.application.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.application.dto.ChatMessageSendResponse;
import com.haagendazs.domain.model.Message;
import com.haagendazs.domain.model.ScheduledMessage;
import com.haagendazs.domain.model.ScheduledMessageStatus;
import com.haagendazs.domain.repository.MessageRepository;
import com.haagendazs.domain.repository.ScheduledMessageRepository;
import com.haagendazs.infrastructure.redis.RedisPubSubService;

import java.time.LocalDateTime;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledMessageScheduler {

    private final ScheduledMessageRepository scheduledMessageRepository;
    private final MessageRepository messageRepository;
    private final RedisPubSubService redisPubSubService;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 60000)   // 1분마다 실행
    @Transactional
    public void sendScheduledMessages() {
        List<ScheduledMessage> targets = scheduledMessageRepository
                .findAllByStatusAndScheduledAtBefore(ScheduledMessageStatus.PENDING, LocalDateTime.now());

        if (targets.isEmpty()) {
            return;
        }

        log.info("예약 메시지 발송 시작, 대상 {}건", targets.size());

        for (ScheduledMessage sm : targets) {
            try {
                // 1. 실제 메시지로 저장
                Message message = Message.builder()
                        .channelId(sm.getChannelId())
                        .senderId(sm.getSenderId())
                        .content(sm.getContent())
                        .build();
                Message saved = messageRepository.save(message);

                // 2. Redis로 브로드캐스트
                ChatMessageSendResponse response = new ChatMessageSendResponse(
                        saved.getMessageId(),
                        saved.getChannelId(),
                        saved.getSenderId(),
                        saved.getContent(),
                        saved.getCreatedAt(),
                        null   // 스케줄 메시지에는 sendAt미적용
                );
                String json = objectMapper.writeValueAsString(response);
                redisPubSubService.publish("chat:" + sm.getChannelId(), json);

                // 3. 상태 변경
                sm.markAsSent();

                log.info("예약 메시지 발송 완료, scheduledMessageId={}", sm.getScheduledMessageId());
            } catch (Exception e) {
                log.error("예약 메시지 발송 실패, scheduledMessageId={}", sm.getScheduledMessageId(), e);
            }
        }
    }
}
