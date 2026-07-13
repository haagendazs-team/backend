package com.haagendazs.member.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.common.event.search.MemberCreatedPayload;
import com.haagendazs.common.event.search.MemberDeactivatedPayload;
import com.haagendazs.common.event.search.MemberUpdatedPayload;
import com.haagendazs.common.event.search.SearchEventTopics;
import com.haagendazs.common.exception.InfrastructureErrorCode;
import com.haagendazs.common.exception.InfrastructureException;
import com.haagendazs.member.application.port.MemberEventPublisher;
import com.haagendazs.member.domain.model.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaMemberEventPublisher implements MemberEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publishCreated(Member member) {
        publish(
                SearchEventTopics.CREATED,
                new MemberCreatedPayload(
                        member.getMemberId(),
                        member.getEmail(),
                        member.getNickname(),
                        member.getProfileImageUrl()
                ),
                member.getMemberId()
        );
    }

    @Override
    public void publishUpdated(Member member) {
        publish(
                SearchEventTopics.UPDATED,
                new MemberUpdatedPayload(
                        member.getMemberId(),
                        member.getEmail(),
                        member.getNickname(),
                        member.getProfileImageUrl()
                ),
                member.getMemberId()
        );
    }

    @Override
    public void publishDeactivated(Member member) {
        publish(
                SearchEventTopics.DEACTIVATED,
                new MemberDeactivatedPayload(member.getMemberId(), Instant.now()),
                member.getMemberId()
        );
    }

    private void publish(String topic, Object payload, Long memberId) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            String key = String.valueOf(memberId);
            kafkaTemplate.send(topic, key, json);
            log.info("Kafka 이벤트 발행 topic={} memberId={}", topic, memberId);
        } catch (JsonProcessingException e) {
            throw new InfrastructureException(InfrastructureErrorCode.KAFKA_PUBLISH_FAILED, e);
        }
    }
}
