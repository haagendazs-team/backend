package com.haagendazs.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.application.service.EmailCertService;
import com.haagendazs.application.service.MemberCreatedService;
import com.haagendazs.infrastructure.config.RedisStreamsConfig;
import com.haagendazs.infrastructure.kafka.dto.EmailCertPayload;
import com.haagendazs.infrastructure.kafka.dto.MemberCreatedPayload;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class KafkaNotificationConsumer {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final EmailCertService emailCertService;
    private final MemberCreatedService memberCreatedService;
    private final ObjectMapper objectMapper;
    private final String bootstrapServers;
    private final List<String> pushTopics;
    private final String emailCertTopic;
    private final String memberCreatedTopic;

    private Disposable subscription;

    public KafkaNotificationConsumer(
            ReactiveStringRedisTemplate redisTemplate,
            EmailCertService emailCertService,
            MemberCreatedService memberCreatedService,
            ObjectMapper objectMapper,
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("#{'${spring.kafka.topics.push}'.split(',')}") List<String> pushTopics,
            @Value("${spring.kafka.topics.email-cert}") String emailCertTopic,
            @Value("${spring.kafka.topics.member-created}") String memberCreatedTopic) {
        this.redisTemplate = redisTemplate;
        this.emailCertService = emailCertService;
        this.memberCreatedService = memberCreatedService;
        this.objectMapper = objectMapper;
        this.bootstrapServers = bootstrapServers;
        this.pushTopics = pushTopics;
        this.emailCertTopic = emailCertTopic;
        this.memberCreatedTopic = memberCreatedTopic;
    }

    @PostConstruct
    void start() {
        List<String> allTopics = new java.util.ArrayList<>(pushTopics);
        allTopics.add(emailCertTopic);
        allTopics.add(memberCreatedTopic);

        ReceiverOptions<String, String> options = ReceiverOptions.<String, String>create(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "notification",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        )).subscription(allTopics);

        subscription = KafkaReceiver.create(options)
                .receive()
                .flatMap(record -> {
                    String payload = record.value();
                    String topic = record.topic();
                    return route(topic, payload)
                            .doOnSuccess(v -> record.receiverOffset().acknowledge())
                            .onErrorResume(e -> {
                                log.error("Kafka 메시지 처리 실패 topic={}", topic, e);
                                return Mono.empty();
                            });
                })
                .subscribe(
                        v -> {},
                        e -> log.error("Kafka consumer 오류", e)
                );

        log.info("Kafka 알림 consumer 시작 topics={}", allTopics);
    }

    @PreDestroy
    void stop() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }

    private Mono<Void> route(String topic, String payload) {
        if (topic.equals(emailCertTopic)) {
            return handleEmailCert(payload);
        }
        if (topic.equals(memberCreatedTopic)) {
            return handleMemberCreated(payload);
        }
        return forwardToRedisStream(payload, topic);
    }

    private Mono<Void> handleEmailCert(String payload) {
        return Mono.fromCallable(() -> objectMapper.readValue(payload, EmailCertPayload.class))
                .flatMap(p -> emailCertService.sendCertificationEmail(p.email(), p.code()));
    }

    private Mono<Void> handleMemberCreated(String payload) {
        return Mono.fromCallable(() -> objectMapper.readValue(payload, MemberCreatedPayload.class))
                .flatMap(p -> memberCreatedService.initialize(p.memberId(), p.email()));
    }

    private Mono<Void> forwardToRedisStream(String payload, String topic) {
        return redisTemplate.opsForStream()
                .add(RedisStreamsConfig.STREAM_KEY, Map.of("payload", payload, "topic", topic))
                .doOnSuccess(id -> log.info("Kafka→Redis Stream 전달 topic={} id={}", topic, id))
                .then();
    }
}
