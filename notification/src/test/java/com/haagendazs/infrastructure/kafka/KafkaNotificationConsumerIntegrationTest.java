package com.haagendazs.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.application.sender.NotificationSender;
import com.haagendazs.domain.model.ChannelType;
import com.haagendazs.domain.repository.ChannelRepository;
import com.haagendazs.infrastructure.config.RedisStreamsConfig;
import com.haagendazs.infrastructure.kafka.dto.EmailCertPayload;
import com.haagendazs.infrastructure.kafka.dto.MemberCreatedPayload;
import com.haagendazs.support.AbstractIntegrationTest;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.SenderRecord;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@DisplayName("KafkaNotificationConsumer 통합 테스트")
class KafkaNotificationConsumerIntegrationTest extends AbstractIntegrationTest {

    @TestConfiguration
    static class MockSenderConfig {
        static final NotificationSender EMAIL_SENDER_MOCK = Mockito.mock(NotificationSender.class,
                Mockito.withSettings().defaultAnswer(invocation -> {
                    if ("channelType".equals(invocation.getMethod().getName())) {
                        return ChannelType.EMAIL;
                    }
                    return Mockito.RETURNS_DEFAULTS.answer(invocation);
                }));

        @Bean
        @Primary
        NotificationSender emailNotificationSender() {
            return EMAIL_SENDER_MOCK;
        }

        @Bean
        @Primary
        JavaMailSender javaMailSender() {
            return Mockito.mock(org.springframework.mail.javamail.JavaMailSender.class);
        }
    }

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationSender emailNotificationSender;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.topics.email-cert}")
    private String emailCertTopic;

    @Value("${spring.kafka.topics.member-created}")
    private String memberCreatedTopic;

    @Value("${spring.kafka.topics.push}")
    private String pushTopics;

    private KafkaSender<String, String> producer;

    @BeforeEach
    void setUp() {
        Mockito.reset(MockSenderConfig.EMAIL_SENDER_MOCK);
        Mockito.when(emailNotificationSender.channelType()).thenReturn(ChannelType.EMAIL);

        SenderOptions<String, String> senderOptions = SenderOptions.create(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
        ));
        producer = KafkaSender.create(senderOptions);
    }

    @Test
    @DisplayName("member.created 토픽 수신 시 EMAIL 채널이 DB에 저장된다")
    void memberCreated_savesEmailChannelToDatabase() throws Exception {
        // given
        Long memberId = 99001L;
        MemberCreatedPayload payload = new MemberCreatedPayload(memberId, "test@example.com", "닉네임", null);
        String json = objectMapper.writeValueAsString(payload);

        // when
        sendMessage(memberCreatedTopic, json);

        // then
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Boolean exists = channelRepository
                    .existsByMemberIdAndChannelType(memberId, ChannelType.EMAIL)
                    .block();
            assertThat(exists).isTrue();
        });
    }

    @Test
    @DisplayName("email-cert 토픽 수신 시 이메일 발송 sender가 호출된다")
    void emailCert_invokesEmailSender() throws Exception {
        // given
        EmailCertPayload payload = new EmailCertPayload("cert@example.com", "987654");
        String json = objectMapper.writeValueAsString(payload);

        // when
        sendMessage(emailCertTopic, json);

        // then
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                verify(emailNotificationSender).send(
                        eq("cert@example.com"),
                        anyString(),
                        anyString()
                )
        );
    }

    @Test
    @DisplayName("push 토픽 수신 시 Redis Stream에 메시지가 적재된다")
    void pushTopic_forwardsMessageToRedisStream() throws Exception {
        // given
        String firstPushTopic = pushTopics.split(",")[0].trim();
        String rawPayload = "{\"eventTypeCode\":\"PAYMENT_COMPLETED\",\"memberId\":99002}";

        // when
        sendMessage(firstPushTopic, rawPayload);

        // then
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Long streamLength = redisTemplate.opsForStream()
                    .size(RedisStreamsConfig.STREAM_KEY)
                    .block();
            assertThat(streamLength).isGreaterThan(0);
        });
    }

    private void sendMessage(String topic, String value) {
        producer.send(
                reactor.core.publisher.Mono.just(
                        SenderRecord.create(
                                new ProducerRecord<>(topic, value),
                                null
                        )
                )
        ).blockLast(Duration.ofSeconds(5));
    }
}
