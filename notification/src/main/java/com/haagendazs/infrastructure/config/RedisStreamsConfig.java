package com.haagendazs.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.stream.StreamReceiver;

import java.time.Duration;

@Slf4j
@Configuration
public class RedisStreamsConfig {

    public static final String NOTIFICATION_GROUP = "notification-group";
    public static final int STREAM_MAX_LEN = 10_000;
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(2);

    @Bean
    public StreamReceiver<String, ObjectRecord<String, String>> streamReceiver(
            ReactiveRedisConnectionFactory connectionFactory,
            ReactiveStringRedisTemplate redisTemplate
    ) {
        var options = StreamReceiver.StreamReceiverOptions.builder()
                .pollTimeout(POLL_TIMEOUT)
                .targetType(String.class)
                .build();

        return StreamReceiver.create(connectionFactory, options);
    }

    private boolean isBusyGroup(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause.getMessage() != null && cause.getMessage().contains("BUSYGROUP")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
