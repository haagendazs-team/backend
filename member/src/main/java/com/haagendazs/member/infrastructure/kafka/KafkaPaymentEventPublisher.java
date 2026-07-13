package com.haagendazs.member.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.common.event.payment.PaymentEventTopics;
import com.haagendazs.common.event.payment.WorkspaceCreatedPayload;
import com.haagendazs.common.exception.InfrastructureErrorCode;
import com.haagendazs.common.exception.InfrastructureException;
import com.haagendazs.member.application.port.PaymentEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publishWorkspaceCreated(Long workspaceId) {
        publish(
                PaymentEventTopics.WORKSPACE_CREATED,
                new WorkspaceCreatedPayload(workspaceId),
                String.valueOf(workspaceId)
        );
    }

    private void publish(String topic, Object payload, String key) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, key, json);
            log.info("Kafka 결제 이벤트 발행 topic={} key={}", topic, key);
        } catch (JsonProcessingException e) {
            throw new InfrastructureException(InfrastructureErrorCode.KAFKA_PUBLISH_FAILED, e);
        }
    }
}
