package com.haagendazs.member.infrastructure.kafka;

import com.haagendazs.common.event.payment.PaymentEventTopics;
import com.haagendazs.common.event.payment.WorkspaceCreatedPayload;
import com.haagendazs.member.application.port.PaymentEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishWorkspaceCreated(Long workspaceId) {
        send(
                PaymentEventTopics.WORKSPACE_CREATED,
                workspaceId.toString(),
                new WorkspaceCreatedPayload(workspaceId)
        );
    }

    private void send(String topic, String key, Object event) {
        kafkaTemplate.send(topic, key, event);
        log.info("Kafka 결제 이벤트 발행 topic={} key={}", topic, key);
    }
}
