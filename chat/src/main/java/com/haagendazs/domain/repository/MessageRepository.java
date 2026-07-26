package com.haagendazs.domain.repository;

import com.haagendazs.domain.model.Message;

import java.util.List;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findAllByChannelIdOrderByMessageIdDesc(Long channelId, Pageable pageable);

    // 스크롤 위로 올릴 때: 특정 messageId 이전의 메시지 N개
    List<Message> findAllByChannelIdAndMessageIdLessThanOrderByMessageIdDesc(
            Long channelId, Long cursor, Pageable pageable
    );
}
