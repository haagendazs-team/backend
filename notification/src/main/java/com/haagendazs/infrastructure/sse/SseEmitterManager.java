package com.haagendazs.infrastructure.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.application.port.SseNotificationPort;
import com.haagendazs.infrastructure.config.NotificationProperties;
import com.haagendazs.infrastructure.config.RedisPubSubConfig;
import com.haagendazs.presentation.dto.NotificationResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseEmitterManager implements SseNotificationPort {

    private static final String SSE_SESSION_KEY_PREFIX = "sse:online:";
    private static final ServerSentEvent<Object> PING_EVENT =
            ServerSentEvent.builder().event("ping").data("").build();

    private final NotificationProperties properties;
    private final MeterRegistry meterRegistry;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.application.name:notification}-${HOSTNAME:local}")
    private String instanceId;

    private final Map<Long, SseSession> sessions = new ConcurrentHashMap<>();
    private Counter sentCounter;
    private Timer sendDurationTimer;

    @PostConstruct
    void initMetrics() {
        Gauge.builder("sse_active_connections", sessions, Map::size).register(meterRegistry);
        sentCounter = Counter.builder("sse_sent_total").register(meterRegistry);
        sendDurationTimer = Timer.builder("sse_send_duration").register(meterRegistry);
    }

    @Override
    public Flux<ServerSentEvent<Object>> subscribe(Long memberId, Flux<ServerSentEvent<Object>> replay) {
        Sinks.Many<ServerSentEvent<Object>> sink = Sinks.many().multicast().onBackpressureBuffer(256, false);
        SseSession previous = sessions.put(memberId, new SseSession(sink));
        if (previous != null) {
            previous.sink().tryEmitComplete();
        }

        Duration sessionTtl = Duration.ofMillis(properties.sse().timeoutMs()).plusMinutes(1);
        redisTemplate.opsForValue()
                .set(SSE_SESSION_KEY_PREFIX + memberId, instanceId, sessionTtl)
                .subscribe(null, e -> log.error("SSE 세션 Redis 등록 실패 memberId={}", memberId, e));

        log.info("SSE subscribed memberId={} instanceId={}", memberId, instanceId);

        return Flux.concat(Flux.just(PING_EVENT), replay, sink.asFlux())
                .timeout(Duration.ofMillis( properties.sse().timeoutMs() ))
                .publishOn(Schedulers.boundedElastic())
                .doFinally(signal -> {
                    sessions.compute(memberId, (_, current) -> {
                        if (current != null && current.sink() == sink) {
                            return null;
                        }
                        return current;
                    });
                    redisTemplate.delete(SSE_SESSION_KEY_PREFIX + memberId).
                            subscribe(null, e -> log.error("SSE 세션 Redis 삭제 실패 memberId={}", memberId, e));
                    log.info("SSE disconnected memberId={} reason={}", memberId, signal);
                })
                .onErrorResume(TimeoutException.class, _ -> Flux.empty())
                .onErrorResume(_ -> {
                    meterRegistry.counter("sse_errors_total", "reason", "error").increment();
                    return Flux.empty();
                });
    }

    @Override
    public void send(Long memberId, Object data) {
        if (!(data instanceof NotificationResponse response)) {
            log.warn("SSE 브로드캐스트 미지원 타입 memberId={} type={}", memberId, data.getClass().getSimpleName());
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(response);
            String message = memberId + ":" + json;
            redisTemplate.convertAndSend(RedisPubSubConfig.SSE_BROADCAST_CHANNEL, message)
                    .subscribe(null, e -> log.error("SSE Redis 발행 실패 memberId={}", memberId, e));
        } catch (JsonProcessingException e) {
            log.error("SSE 브로드캐스트 직렬화 실패 memberId={}", memberId, e);
        }
    }

    public void sendLocal(Long memberId, NotificationResponse response) {
        SseSession session = sessions.get(memberId);
        if (session == null) {
            return;
        }
        long start = System.nanoTime();
        try {
            Sinks.EmitResult result = session.sink().tryEmitNext(buildEvent(response));
            if (result.isSuccess()) {
                sentCounter.increment();
            } else {
                log.warn("SSE 알림 emit 드롭 memberId={} result={}", memberId, result);
                meterRegistry.counter("sse_errors_total", "reason", "emit_dropped").increment();
            }
        } finally {
            sendDurationTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    @Scheduled(fixedRate = 30_000)
    public void sendHeartbeat() {
        int active = sessions.size();
        sessions.forEach((memberId, session) ->
                session.sink().emitNext(PING_EVENT, Sinks.EmitFailureHandler.FAIL_FAST));
        log.info("heartbeat 완료 active={}", active);
    }

    @Override
    public Mono<Boolean> isConnected(Long memberId) {
        if (sessions.containsKey(memberId)) {
            return Mono.just(true);
        }
        return redisTemplate.hasKey(SSE_SESSION_KEY_PREFIX + memberId);
    }

    private ServerSentEvent<Object> buildEvent(NotificationResponse response) {
        return ServerSentEvent.builder()
                .id(String.valueOf(response.id()))
                .event("notification")
                .data(response)
                .build();
    }

    private static final class SseSession {
        private final Sinks.Many<ServerSentEvent<Object>> sink;

        SseSession(Sinks.Many<ServerSentEvent<Object>> sink) {
            this.sink = sink;
        }

        Sinks.Many<ServerSentEvent<Object>> sink() {
            return sink;
        }
    }
}
