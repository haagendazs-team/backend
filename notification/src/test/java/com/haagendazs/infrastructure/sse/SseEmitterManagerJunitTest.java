package com.haagendazs.infrastructure.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.haagendazs.domain.model.Channel;
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
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SseEmitterManagerJunitTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOps;

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

        when(properties.sse()).thenReturn(new NotificationProperties.Sse(30000L, 30000L, "0/30 * * * * *"));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        sseEmitterManager = new SseEmitterManager(properties, meterRegistry, redisTemplate, objectMapper);
        injectInstanceId(sseEmitterManager, "notification-test");
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

    @Test
    @DisplayName("sendLocal() — emit 성공 시 sse_sent_total 카운터 증가")
    void sendLocal_success_incrementsSentCounter() {
        NotificationResponse response = new NotificationResponse(
                1L, "GAME_START", "payload", false, Instant.now()
        );
        sseEmitterManager.subscribe(1L, Flux.empty()).subscribe();

        sseEmitterManager.sendLocal(1L, response);

        double count = meterRegistry.counter("sse_sent_total").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("sendHeartbeat() — 활성 세션에 ping 이벤트 전달")
    void sendHeartbeat_deliversPingToActiveSessions() {
        Flux<ServerSentEvent<Object>> stream = sseEmitterManager.subscribe(1L, Flux.empty())
                .take(1);

        // publishOn(boundedElastic) 때문에 emit과 onNext 처리가 비동기 — 별도 스레드에서 heartbeat 발송
        new Thread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            sseEmitterManager.sendHeartbeat();
        }).start();

        ServerSentEvent<Object> event = stream.blockFirst(Duration.ofSeconds(3));

        assertThat(event).isNotNull();
        assertThat(event.event()).isEqualTo("ping");
    }

    @Test
    @DisplayName("subscribe() — 동일 memberId 재연결 시 기존 세션 종료")
    void subscribe_reconnect_terminatesPreviousSession() {
        List<ServerSentEvent<Object>> first = new ArrayList<>();

        sseEmitterManager.subscribe(1L,Flux.empty())
                .subscribe(first::add, e -> {}, () -> {});

        sseEmitterManager.subscribe(1L, Flux.empty()).subscribe();

        // 이전 세션은 complete 신호로 종료되어 추가 이벤트 수신 불가
        int countBefore = first.size();
        sseEmitterManager.sendLocal(1L, new NotificationResponse(1L, "X", "p", false, Instant.now()));
        assertThat(first.size()).isEqualTo(countBefore);
    }

    @Test
    @DisplayName("sendLocal() + sendHeartbeat() 동시 호출 — FAIL_NON_SERIALIZED 없이 처리됨")
    void sendLocal_and_sendHeartbeat_concurrent_noSerializationFailure() throws InterruptedException {
        int threadCount = 10;
        // sse_errors_total{reason=emit_failed} 카운터로 직렬화 실패 감지
        sseEmitterManager.subscribe(1L,Flux.empty()).subscribe();

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        var executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (threadId % 2 == 0) {
                    for (int j = 0; j < 30; j++) {
                        sseEmitterManager.sendLocal(1L, new NotificationResponse(
                                (long) j, "EVT", "p", false, Instant.now()
                        ));
                    }
                } else {
                    for (int j = 0; j < 10; j++) {
                        sseEmitterManager.sendHeartbeat();
                    }
                }
                done.countDown();
            });
        }

        start.countDown();
        done.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        double failures = meterRegistry.counter("sse_errors_total", "reason", "emit_failed").count();
        assertThat(failures).isEqualTo(0.0);
    }

    private static void injectInstanceId(SseEmitterManager target, String value) {
        try {
            Field field = SseEmitterManager.class.getDeclaredField("instanceId");
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
