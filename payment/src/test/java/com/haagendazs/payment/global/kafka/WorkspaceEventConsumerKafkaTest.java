package com.haagendazs.payment.global.kafka;

import com.haagendazs.TestPaymentApplication;
import com.haagendazs.infrastructure.kafka.dto.WorkspaceCreatedEvent;
import com.haagendazs.application.service.SubscriptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest(
        classes = TestPaymentApplication.class,
        properties = "spring.kafka.listener.auto-startup=true"
)
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = "payment-test.workspace-created.v1",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class WorkspaceEventConsumerKafkaTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockitoBean
    private SubscriptionService subscriptionService;

    @Value("${spring.kafka.topics.workspace-created}")
    private String workspaceCreatedTopic;

    @Test
    @DisplayName("workspace-created Kafka 메시지를 수신해 기본 구독 생성을 호출한다")
    void handleWorkspaceCreated_receivesKafkaMessage() {
        kafkaTemplate.send(
                workspaceCreatedTopic,
                "3001",
                new WorkspaceCreatedEvent(3001L)
        );
        kafkaTemplate.flush();

        verify(subscriptionService, timeout(10_000))
                .createDefaultSubscriptionIfAbsent(3001L);
    }
}
