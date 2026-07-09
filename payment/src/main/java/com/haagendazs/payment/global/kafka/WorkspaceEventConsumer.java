package com.haagendazs.payment.global.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.payment.global.PaymentErrorCode;
import com.haagendazs.payment.global.kafka.dto.WorkspaceCreatedEvent;
import com.haagendazs.payment.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkspaceEventConsumer {

    private final ObjectMapper objectMapper;
    private final SubscriptionService subscriptionService;

    @KafkaListener(topics = "${spring.kafka.topics.workspace-created}")
    public void handleWorkspaceCreated(String payload) {
        try {
            WorkspaceCreatedEvent event =
                    objectMapper.readValue(payload, WorkspaceCreatedEvent.class);

            subscriptionService.createDefaultSubscriptionIfAbsent(
                    event.workspaceId()
            );

        } catch (JsonProcessingException e) {
            throw new BusinessException(PaymentErrorCode.KAFKA_MESSAGE_DESERIALIZE_FAILED);
        }
    }
}
