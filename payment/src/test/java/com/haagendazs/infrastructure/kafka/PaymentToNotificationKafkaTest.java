package com.haagendazs.infrastructure.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.infrastructure.config.RedisStreamsConfig;
import com.haagendazs.TestPaymentApplication;
import com.haagendazs.infrastructure.kafka.dto.PaymentNotificationPayload;
import com.haagendazs.infrastructure.kafka.dto.SubscriptionChangedEvent;
import com.haagendazs.infrastructure.kafka.dto.SubscriptionInitializedEvent;
import com.haagendazs.infrastructure.kafka.dto.WorkspaceSubscribedEvent;
import com.haagendazs.infrastructure.kafka.dto.WorkspaceSubscriptionExpiredEvent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.ReactiveStreamOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = TestPaymentApplication.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@EmbeddedKafka(
        partitions = 1,
        topics = {
                "payment-test.subscription-initialized.v1",
                "payment-test.subscription-changed.v1",
                "payment-test.notif.push.v1",
                "payment-test.workspace-subscribed.v1",
                "payment-test.workspace-subscription-expired.v1"
        },
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class PaymentToNotificationKafkaTest {

    @Autowired
    private PaymentEventProducer paymentEventProducer;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${spring.kafka.topics.payment-notification}")
    private String paymentNotificationTopic;

    @Value("${spring.kafka.topics.subscription-initialized}")
    private String subscriptionInitializedTopic;

    @Value("${spring.kafka.topics.subscription-changed}")
    private String subscriptionChangedTopic;

    @Value("${spring.kafka.topics.workspace-subscribed}")
    private String workspaceSubscribedTopic;

    @Value("${spring.kafka.topics.workspace-subscription-expired}")
    private String workspaceSubscriptionExpiredTopic;

    private KafkaNotificationConsumer notificationConsumer;

    @AfterEach
    void tearDown() {
        if (notificationConsumer != null) {
            notificationConsumer.stop();
        }
    }

    @Test
    @DisplayName("publishPaymentCompleted() — notification consumer가 수신한다")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void publishPaymentCompleted_notificationConsumerReceivesIt() throws Exception {
        ReactiveStreamOperations<String, Object, Object> streamOps = startNotificationConsumer();

        paymentEventProducer.publishPaymentCompleted(1001L, paymentPayload("COMPLETED"));
        kafkaTemplate.flush();

        JsonNode event = capturedNotificationEvent(streamOps);
        assertThat(event.get("eventTypeCode").asText()).isEqualTo("PAYMENT_COMPLETED");
        assertThat(event.get("memberId").asText()).isEqualTo("1001");
        assertThat(event.get("DispatchType").asText()).isEqualTo("IMMEDIATE");
        assertThat(event.get("scheduledAt").isNull()).isTrue();
        assertThat(event.get("payload").get("paymentStatus").asText()).isEqualTo("COMPLETED");
        assertThat(event.get("payload").get("orderNo").asText()).isEqualTo("ORDER-20260720-001");
        assertThat(event.get("payload").get("workspaceId").asLong()).isEqualTo(3001L);
    }

    @Test
    @DisplayName("publishPaymentFailed() — notification consumer가 수신한다")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void publishPaymentFailed_notificationConsumerReceivesIt() throws Exception {
        ReactiveStreamOperations<String, Object, Object> streamOps = startNotificationConsumer();

        paymentEventProducer.publishPaymentFailed(1001L, paymentPayload("FAILED"));
        kafkaTemplate.flush();

        JsonNode event = capturedNotificationEvent(streamOps);
        assertThat(event.get("eventTypeCode").asText()).isEqualTo("PAYMENT_FAILED");
        assertThat(event.get("memberId").asText()).isEqualTo("1001");
        assertThat(event.get("payload").get("paymentStatus").asText()).isEqualTo("FAILED");
        assertThat(event.get("payload").get("failCode").asText()).isEqualTo("CARD_DECLINED");
        assertThat(event.get("payload").get("failMessage").asText()).isEqualTo("카드 승인 실패");
    }

    @Test
    @DisplayName("publishSubscriptionInitialized() — Kafka consumer가 수신한다")
    void publishSubscriptionInitialized_consumerReceivesIt() throws Exception {
        try (Consumer<String, String> consumer = createConsumer("subscription-initialized-test")) {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, subscriptionInitializedTopic);
            LocalDateTime initializedAt = LocalDateTime.of(2026, 7, 20, 10, 0);

            paymentEventProducer.publishSubscriptionInitialized(
                    new SubscriptionInitializedEvent(3001L, 30, initializedAt)
            );
            kafkaTemplate.flush();

            ConsumerRecord<String, String> record = getSingleRecord(consumer, subscriptionInitializedTopic);
            JsonNode event = objectMapper.readTree(record.value());

            assertThat(record.key()).isEqualTo("3001");
            assertThat(event.get("workspaceId").asLong()).isEqualTo(3001L);
            assertThat(event.get("searchableDays").asInt()).isEqualTo(30);
            assertThat(event.get("initializedAt").asText()).startsWith("2026-07-20T10:00");
        }
    }

    @Test
    @DisplayName("publishSubscriptionChanged() — Kafka consumer가 수신한다")
    void publishSubscriptionChanged_consumerReceivesIt() throws Exception {
        try (Consumer<String, String> consumer = createConsumer("subscription-changed-test")) {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, subscriptionChangedTopic);
            LocalDateTime changedAt = LocalDateTime.of(2026, 7, 20, 11, 0);

            paymentEventProducer.publishSubscriptionChanged(
                    new SubscriptionChangedEvent(3001L, 90, changedAt)
            );
            kafkaTemplate.flush();

            ConsumerRecord<String, String> record = getSingleRecord(consumer, subscriptionChangedTopic);
            JsonNode event = objectMapper.readTree(record.value());

            assertThat(record.key()).isEqualTo("3001");
            assertThat(event.get("workspaceId").asLong()).isEqualTo(3001L);
            assertThat(event.get("searchableDays").asInt()).isEqualTo(90);
            assertThat(event.get("changedAt").asText()).startsWith("2026-07-20T11:00");
        }
    }

    @Test
    @DisplayName("publishWorkspaceSubscribed() — Kafka consumer가 수신한다")
    void publishWorkspaceSubscribed_consumerReceivesIt() throws Exception {
        try (Consumer<String, String> consumer = createConsumer("workspace-subscribed-test")) {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, workspaceSubscribedTopic);
            LocalDateTime activatedAt = LocalDateTime.of(2026, 7, 20, 12, 0);

            paymentEventProducer.publishWorkspaceSubscribed(
                    new WorkspaceSubscribedEvent(3001L, "PRO", activatedAt)
            );
            kafkaTemplate.flush();

            ConsumerRecord<String, String> record = getSingleRecord(consumer, workspaceSubscribedTopic);
            JsonNode event = objectMapper.readTree(record.value());

            assertThat(record.key()).isEqualTo("3001");
            assertThat(event.get("workspace_id").asLong()).isEqualTo(3001L);
            assertThat(event.get("subscription").asText()).isEqualTo("PRO");
            assertThat(event.get("activated_at").asText()).startsWith("2026-07-20T12:00");
        }
    }

    @Test
    @DisplayName("publishWorkspaceSubscriptionExpired() — Kafka consumer가 수신한다")
    void publishWorkspaceSubscriptionExpired_consumerReceivesIt() throws Exception {
        try (Consumer<String, String> consumer = createConsumer("workspace-subscription-expired-test")) {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, workspaceSubscriptionExpiredTopic);
            LocalDateTime expiredAt = LocalDateTime.of(2026, 7, 20, 13, 0);

            paymentEventProducer.publishWorkspaceSubscriptionExpired(
                    new WorkspaceSubscriptionExpiredEvent(3001L, "PRO", expiredAt)
            );
            kafkaTemplate.flush();

            ConsumerRecord<String, String> record = getSingleRecord(consumer, workspaceSubscriptionExpiredTopic);
            JsonNode event = objectMapper.readTree(record.value());

            assertThat(record.key()).isEqualTo("3001");
            assertThat(event.get("workspace_id").asLong()).isEqualTo(3001L);
            assertThat(event.get("subscription").asText()).isEqualTo("PRO");
            assertThat(event.get("expired_at").asText()).startsWith("2026-07-20T13:00");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ReactiveStreamOperations<String, Object, Object> startNotificationConsumer() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        ReactiveStreamOperations<String, Object, Object> streamOps = mock(ReactiveStreamOperations.class);
        when(redisTemplate.opsForStream()).thenReturn((ReactiveStreamOperations) streamOps);
        when(streamOps.add(eq(RedisStreamsConfig.STREAM_KEY), anyMap()))
                .thenReturn(Mono.just(RecordId.of("1-0")));

        notificationConsumer = new KafkaNotificationConsumer(
                redisTemplate,
                embeddedKafkaBroker.getBrokersAsString(),
                java.util.List.of(paymentNotificationTopic)
        );
        notificationConsumer.start();
        return streamOps;
    }

    private JsonNode capturedNotificationEvent(
            ReactiveStreamOperations<String, Object, Object> streamOps
    ) throws Exception {
        ArgumentCaptor<Map> redisMessageCaptor = ArgumentCaptor.forClass(Map.class);
        verify(streamOps, timeout(10_000))
                .add(eq(RedisStreamsConfig.STREAM_KEY), redisMessageCaptor.capture());

        Map<?, ?> redisMessage = redisMessageCaptor.getValue();
        assertThat(redisMessage.get("topic")).isEqualTo(paymentNotificationTopic);
        return objectMapper.readTree((String) redisMessage.get("payload"));
    }

    private PaymentNotificationPayload paymentPayload(String paymentStatus) {
        boolean failed = "FAILED".equals(paymentStatus);
        return new PaymentNotificationPayload(
                paymentStatus,
                "ORDER-20260720-001",
                2001L,
                3001L,
                15000L,
                "PRO 플랜",
                "https://example.com/receipt",
                failed ? "CARD_DECLINED" : null,
                failed ? "카드 승인 실패" : null,
                LocalDateTime.of(2026, 7, 20, 10, 30)
        );
    }

    private Consumer<String, String> createConsumer(String groupId) {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                embeddedKafkaBroker,
                groupId,
                true
        );
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(
                consumerProps,
                new StringDeserializer(),
                new StringDeserializer()
        ).createConsumer();
    }

    private ConsumerRecord<String, String> getSingleRecord(
            Consumer<String, String> consumer,
            String topic
    ) {
        return KafkaTestUtils.getSingleRecord(consumer, topic, Duration.ofSeconds(10));
    }
}
