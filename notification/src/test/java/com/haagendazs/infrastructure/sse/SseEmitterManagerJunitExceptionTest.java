package com.haagendazs.infrastructure.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.haagendazs.infrastructure.config.NotificationProperties;
import com.haagendazs.presentation.dto.NotificationResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SseEmitterManagerJunitExceptionTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private NotificationProperties properties;

    private MeterRegistry meterRegistry;
    private SseEmitterManager sseEmitterManager;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        meterRegistry = new SimpleMeterRegistry();
        when(properties.sse()).thenReturn(new NotificationProperties.Sse(30000L, 30000L, "0/30 * * * * *"));

        sseEmitterManager = new SseEmitterManager(properties, meterRegistry, redisTemplate, objectMapper);
        sseEmitterManager.initMetrics();
    }

    @Test
    @DisplayName("send() — NotificationResponse 아닌 타입 전달 시 예외 전파 없음")
    void send_nonNotificationResponseType_noExceptionPropagated() {
        sseEmitterManager.send(1L, "unsupported-string-type");
    }

    @Test
    @DisplayName("send() — Redis 발행 실패 시 예외 전파 없음")
    void send_redisPublishError_noExceptionPropagated() {
        NotificationResponse response = new NotificationResponse(
                1L, "GAME_START", "payload", false, Instant.now()
        );
        when(redisTemplate.convertAndSend(anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("Redis 연결 실패")));

        sseEmitterManager.send(1L, response);
    }
}
