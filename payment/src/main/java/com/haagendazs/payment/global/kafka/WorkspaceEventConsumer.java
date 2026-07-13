package com.haagendazs.payment.global.kafka;

import com.haagendazs.payment.global.kafka.dto.WorkspaceCreatedEvent;
import com.haagendazs.payment.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkspaceEventConsumer {

    private final SubscriptionService subscriptionService;

    @KafkaListener(topics = "${spring.kafka.topics.workspace-created}")
    public void handleWorkspaceCreated(WorkspaceCreatedEvent event) {
        subscriptionService.createDefaultSubscriptionIfAbsent(
                event.workspaceId()
        );
    }
}
