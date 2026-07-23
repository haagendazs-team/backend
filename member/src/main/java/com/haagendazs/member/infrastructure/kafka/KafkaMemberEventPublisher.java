package com.haagendazs.member.infrastructure.kafka;

import com.haagendazs.common.event.search.MemberCreatedPayload;
import com.haagendazs.common.event.search.MemberDeactivatedPayload;
import com.haagendazs.common.event.search.MemberUpdatedPayload;
import com.haagendazs.common.event.search.SearchEventTopics;
import com.haagendazs.member.application.port.MemberEventPublisher;
import com.haagendazs.member.domain.model.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaMemberEventPublisher implements MemberEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishCreated(Member member) {
        send(
                SearchEventTopics.CREATED,
                member.getMemberId().toString(),
                new MemberCreatedPayload(
                        member.getMemberId(),
                        member.getEmail(),
                        member.getNickname(),
                        member.getProfileImageUrl()
                )
        );
    }

    @Override
    public void publishUpdated(Member member) {
        send(
                SearchEventTopics.UPDATED,
                member.getMemberId().toString(),
                new MemberUpdatedPayload(
                        member.getMemberId(),
                        member.getEmail(),
                        member.getNickname(),
                        member.getProfileImageUrl()
                )
        );
    }

    @Override
    public void publishDeactivated(Member member) {
        send(
                SearchEventTopics.DEACTIVATED,
                member.getMemberId().toString(),
                new MemberDeactivatedPayload(member.getMemberId(), Instant.now())
        );
    }

    private void send(String topic, String key, Object event) {
        kafkaTemplate.send(topic, key, event);
        log.info("Kafka 이벤트 발행 topic={} key={}", topic, key);
    }
}
