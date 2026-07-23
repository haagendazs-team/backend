package com.haagendazs.infrastructure.kafka;

import com.haagendazs.infrastructure.kafka.dto.SubscriptionChangedEvent;
import com.haagendazs.infrastructure.kafka.dto.SubscriptionInitializedEvent;
import com.haagendazs.infrastructure.kafka.dto.NotificationPushEvent;
import com.haagendazs.infrastructure.kafka.dto.PaymentNotificationPayload;
import com.haagendazs.infrastructure.kafka.dto.WorkspaceSubscribedEvent;
import com.haagendazs.infrastructure.kafka.dto.WorkspaceSubscriptionExpiredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventProducer { //Kafka 발행 Producer

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.topics.subscription-initialized}")
    private String subscriptionInitializedTopic;

    @Value("${spring.kafka.topics.subscription-changed}")
    private String subscriptionChangedTopic;

    @Value("${spring.kafka.topics.payment-notification}")
    private String paymentNotificationTopic;

    @Value("${spring.kafka.topics.workspace-subscribed}")
    private String workspaceSubscribedTopic;

    @Value("${spring.kafka.topics.workspace-subscription-expired}")
    private String workspaceSubscriptionExpiredTopic;

    public void publishSubscriptionInitialized(SubscriptionInitializedEvent event) {
        send(subscriptionInitializedTopic, String.valueOf(event.workspaceId()), event);
    }

    public void publishSubscriptionChanged(SubscriptionChangedEvent event) {
        send(subscriptionChangedTopic, String.valueOf(event.workspaceId()), event);
    }

    public void publishPaymentCompleted(Long memberId, PaymentNotificationPayload payload) {
        publishPaymentNotification("PAYMENT_COMPLETED", memberId, payload);
    }

    public void publishPaymentFailed(Long memberId, PaymentNotificationPayload payload) {
        publishPaymentNotification("PAYMENT_FAILED", memberId, payload);
    }

    public void publishWorkspaceSubscribed(WorkspaceSubscribedEvent event) {
        send(workspaceSubscribedTopic, String.valueOf(event.workspace_id()), event);
    }

    public void publishWorkspaceSubscriptionExpired(WorkspaceSubscriptionExpiredEvent event) {
        send(workspaceSubscriptionExpiredTopic, String.valueOf(event.workspace_id()), event);
    }

    private void publishPaymentNotification(
            String eventTypeCode,
            Long memberId,
            PaymentNotificationPayload payload
    ) {
        NotificationPushEvent event = NotificationPushEvent.immediate(
                eventTypeCode,
                memberId,
                payload
        );
        send(paymentNotificationTopic, String.valueOf(memberId), event);
    }

    private void send(String topic, String key, Object event) {
        kafkaTemplate.send(topic, key, event);
    }
}
