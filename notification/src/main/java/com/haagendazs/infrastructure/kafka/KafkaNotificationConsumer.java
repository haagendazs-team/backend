package com.haagendazs.infrastructure.kafka;

import com.haagendazs.infrastructure.config.RedisStreamsConfig;
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
    private final String bootstrapServers;
    private final List<String> pushTopics;
    private Disposable subscription;

    public KafkaNotificationConsumer(
            ReactiveStringRedisTemplate redisTemplate,
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("#{'${spring.kafka.topics.push}'.split(',')}") List<String> pushTopics) {
        this.redisTemplate = redisTemplate;
        this.bootstrapServers = bootstrapServers;
        this.pushTopics = pushTopics;
    }

    @PostConstruct
    void start() {
        ReceiverOptions<String, String> options = ReceiverOptions.<String, String>create(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "notification",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        )).subscription(pushTopics);

        subscription = KafkaReceiver.create(options)
                .receive()
                .flatMap(record -> {
                    String payload = record.value();
                    String topic = record.topic();
                    return forwardToRedisStream(payload, topic)
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

        log.info("Kafka 알림 consumer 시작 topics={}", pushTopics);
    }

    @PreDestroy
    void stop() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }

    private Mono<Void> forwardToRedisStream(String payload, String topic) {
        return redisTemplate.opsForStream()
                .add(RedisStreamsConfig.STREAM_KEY, Map.of("payload", payload, "topic", topic))
                .doOnSuccess(id -> log.info("Kafka→Redis Stream 전달 topic={} id={}", topic, id))
                .then();
    }
}
