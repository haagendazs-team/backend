package com.haagendazs.member.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.common.event.notification.MemberEmailCertPayload;
import com.haagendazs.common.event.notification.NotificationEventTopics;
import com.haagendazs.common.event.notification.PushNotificationPayload;
import com.haagendazs.common.exception.InfrastructureErrorCode;
import com.haagendazs.common.exception.InfrastructureException;
import com.haagendazs.member.application.port.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaNotificationEventPublisher implements NotificationEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publishEmailCert(String email, String code) {
        publish(
                NotificationEventTopics.MEMBER_EMAIL_CERT,
                new MemberEmailCertPayload(email, code),
                email
        );
    }

    @Override
    public void publishPush(PushNotificationPayload payload) {
        publish(
                NotificationEventTopics.MEMBER_PUSH,
                payload,
                payload.memberId()
        );
    }

    private void publish(String topic, Object message, String key) {
        try {
            String json = objectMapper.writeValueAsString(message);
            kafkaTemplate.send(topic, key, json);
            log.info("Kafka 알림 이벤트 발행 topic={} key={}", topic, key);
        } catch (JsonProcessingException e) {
            throw new InfrastructureException(InfrastructureErrorCode.KAFKA_PUBLISH_FAILED, e);
        }
    }
}
