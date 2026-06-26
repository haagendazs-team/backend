package com.haagendazs.infrastructure.kafka;

import com.haagendazs.infrastructure.registry.EventTypeRegistry;
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

    static final List<String> SUBSCRIBED_TOPICS = List.of(
            "member.joined",
            "member.withdrawn",
            "payment.completed",
            "payment.failed",
            "payment.refunded"
    );

    private final ReactiveStringRedisTemplate redisTemplate;
    private final EventTypeRegistry registry;
    private final String bootstrapServers;
    private Disposable subscription;

    public KafkaNotificationConsumer(
            ReactiveStringRedisTemplate redisTemplate,
            EventTypeRegistry registry,
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        this.redisTemplate = redisTemplate;
        this.registry = registry;
        this.bootstrapServers = bootstrapServers;
    }

    @PostConstruct
    void start() {
        ReceiverOptions<String, String> options = ReceiverOptions.<String, String>create(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "notification",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        )).subscription(SUBSCRIBED_TOPICS);

        subscription = KafkaReceiver.create(options)
                .receive()
                .flatMap(record -> {
                    String topic = record.topic();
                    String payload = record.value();
                    return forwardToRedisStream(topic, payload)
                            .doOnSuccess(v -> record.receiverOffset().acknowledge())
                            .onErrorResume(e -> {
                                log.error("Kafka 메시지 처리 실패 topic={} payload={}", topic, payload, e);
                                return Mono.empty();
                            });
                })
                .subscribe(
                        v -> {},
                        e -> log.error("Kafka consumer 오류", e)
                );
    }

    @PreDestroy
    void stop() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }

    private Mono<Void> forwardToRedisStream(String topic, String payload) {
        String eventTypeCode = topicToEventTypeCode(topic);
        return registry.getByCode(eventTypeCode)
                .map(def -> redisTemplate.opsForStream()
                        .add(def.getStreamKey(), Map.of("payload", payload))
                        .doOnSuccess(id -> log.info("Kafka→Redis Stream 전달 topic={} streamKey={} id={}", topic, def.getStreamKey(), id))
                        .then())
                .orElseGet(() -> {
                    log.warn("등록되지 않은 이벤트 타입 — 처리 건너뜀 topic={}", topic);
                    return Mono.empty();
                });
    }

    private String topicToEventTypeCode(String topic) {
        return topic.replace(".", "_").toUpperCase();
    }
}
