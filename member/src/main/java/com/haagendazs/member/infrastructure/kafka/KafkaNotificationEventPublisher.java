package com.haagendazs.member.infrastructure.kafka;

import com.haagendazs.common.event.notification.MemberEmailCertPayload;
import com.haagendazs.common.event.notification.NotificationEventTopics;
import com.haagendazs.common.event.notification.PushNotificationPayload;
import com.haagendazs.member.application.port.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaNotificationEventPublisher implements NotificationEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishEmailCert(String email, String code) {
        send(
                NotificationEventTopics.MEMBER_EMAIL_CERT,
                email,
                new MemberEmailCertPayload(email, code)
        );
    }

    @Override
    public void publishPush(PushNotificationPayload payload) {
        send(
                NotificationEventTopics.MEMBER_PUSH,
                payload.memberId(),
                payload
        );
    }

    private void send(String topic, String key, Object event) {
        kafkaTemplate.send(topic, key, event);
        log.info("Kafka 알림 이벤트 발행 topic={} key={}", topic, key);
    }
}
