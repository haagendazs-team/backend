package com.haagendazs.domain.repository;

import com.haagendazs.domain.model.ScheduledMessage;

import com.haagendazs.domain.model.ScheduledMessageStatus;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduledMessageRepository extends JpaRepository<ScheduledMessage, Long> {

    // 스케줄러용: 발송 시간이 된 PENDING 메시지들
    List<ScheduledMessage> findAllByStatusAndScheduledAtBefore(
            ScheduledMessageStatus status, LocalDateTime time
    );

    // 채널별 예약 메시지 목록 (내가 등록한 것)
    List<ScheduledMessage> findAllByChannelIdAndSenderIdAndStatus(
            Long channelId, Long senderId, ScheduledMessageStatus status
    );

}
