package com.haagendazs.payment.global.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.payment.global.PaymentErrorCode;
import com.haagendazs.payment.global.kafka.dto.SubscriptionChangedEvent;
import com.haagendazs.payment.global.kafka.dto.SubscriptionInitializedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventProducer { //Kafka 발행 Producer

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.kafka.topics.subscription-initialized}")
    private String subscriptionInitializedTopic;

    @Value("${spring.kafka.topics.subscription-changed}")
    private String subscriptionChangedTopic;

    public void publishSubscriptionInitialized(SubscriptionInitializedEvent event) {
        send(subscriptionInitializedTopic, String.valueOf(event.workspaceId()), event);
    }

    public void publishSubscriptionChanged(SubscriptionChangedEvent event) {
        send(subscriptionChangedTopic, String.valueOf(event.workspaceId()), event);
    }

    private void send(String topic, String key, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, key, payload);
        } catch (JsonProcessingException e) {
            throw new BusinessException(PaymentErrorCode.KAFKA_MESSAGE_SERIALIZE_FAILED);
        }
    }
}
