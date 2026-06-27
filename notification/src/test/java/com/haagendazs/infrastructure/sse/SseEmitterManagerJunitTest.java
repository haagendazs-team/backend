package com.haagendazs.infrastructure.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.haagendazs.infrastructure.config.NotificationProperties;
import com.haagendazs.infrastructure.config.RedisPubSubConfig;
import com.haagendazs.presentation.dto.NotificationResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SseEmitterManagerJunitTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private NotificationProperties properties;

    private ObjectMapper objectMapper;
    private MeterRegistry meterRegistry;
    private SseEmitterManager sseEmitterManager;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        meterRegistry = new SimpleMeterRegistry();

        when(properties.sse()).thenReturn(new NotificationProperties.Sse(30000L, "0 * * * * *", 5000L));

        sseEmitterManager = new SseEmitterManager(properties, meterRegistry, redisTemplate, objectMapper);
        sseEmitterManager.initMetrics();
    }

    @Test
    @DisplayName("send() — Redis 채널에 memberId:JSON 형식으로 발행")
    void send_publishesJsonWithMemberIdPrefixToRedis() throws Exception {
        NotificationResponse response = new NotificationResponse(
                42L, "GAME_START", "payload", false, Instant.parse("2026-06-27T00:00:00Z")
        );
        when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(Mono.just(1L));

        sseEmitterManager.send(42L, response);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq(RedisPubSubConfig.SSE_BROADCAST_CHANNEL), messageCaptor.capture());

        String published = messageCaptor.getValue();
        assertThat(published).startsWith("42:");
        assertThat(published).contains("\"eventTypeCode\":\"GAME_START\"");
        assertThat(published).contains("\"id\":42");
    }

    @Test
    @DisplayName("send() → publish 문자열 → readValue 라운드트립 — 역직렬화 후 원본과 동일")
    void send_roundTrip_deserializesToOriginalResponse() throws Exception {
        NotificationResponse original = new NotificationResponse(
                7L, "TICKET_OPEN", "seat=A1", false, Instant.parse("2026-06-27T12:00:00Z")
        );
        when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(Mono.just(1L));

        sseEmitterManager.send(7L, original);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq(RedisPubSubConfig.SSE_BROADCAST_CHANNEL), captor.capture());

        String published = captor.getValue();
        int separatorIndex = published.indexOf(':');
        long memberId = Long.parseLong(published.substring(0, separatorIndex));
        String json = published.substring(separatorIndex + 1);
        NotificationResponse deserialized = objectMapper.readValue(json, NotificationResponse.class);

        assertThat(memberId).isEqualTo(7L);
        assertThat(deserialized.id()).isEqualTo(original.id());
        assertThat(deserialized.eventTypeCode()).isEqualTo(original.eventTypeCode());
        assertThat(deserialized.payload()).isEqualTo(original.payload());
        assertThat(deserialized.read()).isEqualTo(original.read());
        assertThat(deserialized.createdAt()).isEqualTo(original.createdAt());
    }

    @Test
    @DisplayName("sendLocal() — 세션 없는 memberId에 예외 발생 없음")
    void sendLocal_unknownMemberId_noException() {
        NotificationResponse response = new NotificationResponse(
                99L, "GAME_START", "payload", false, Instant.now()
        );

        sseEmitterManager.sendLocal(99L, response);
    }
}
