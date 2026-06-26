package com.haagendazs.infrastructure.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.application.port.SseNotificationPort;
import com.haagendazs.domain.model.Channel;
import com.haagendazs.domain.model.Setting;
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
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseEmitterManager implements SseNotificationPort {

    private final NotificationProperties properties;
    private final MeterRegistry meterRegistry;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final Map<Long, SseSession> sessions = new ConcurrentHashMap<>();
    private Counter sentCounter;
    private Timer sendDurationTimer;

    @PostConstruct
    void initMetrics() {
        Gauge.builder("sse_active_connections", sessions, Map::size)
                .register(meterRegistry);
        sentCounter = Counter.builder("sse_sent_total").register(meterRegistry);
        sendDurationTimer = Timer.builder("sse_send_duration").register(meterRegistry);
    }

    @Override
    public Flux<ServerSentEvent<Object>> subscribe(Long memberId, Setting setting, List<Channel> channels) {
        Sinks.Many<ServerSentEvent<Object>> sink = Sinks.many().multicast().directBestEffort();
        SseSession previous = sessions.put(memberId, new SseSession(sink, setting, channels));
        if (previous != null) {
            previous.sink().tryEmitComplete();
        }
        log.info("SSE subscribed memberId={}", memberId);

        return sink.asFlux()
                .timeout(Duration.ofMillis(properties.sse().timeoutMs()))
                .doFinally(signal -> {
                    sessions.compute(memberId, (id, current) -> {
                        if (current != null && current.sink() == sink) {
                            return null;
                        }
                        return current;
                    });
                    log.info("SSE disconnected memberId={} reason={}", memberId, signal);
                })
                .onErrorResume(e -> {
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
            ServerSentEvent<Object> event = buildEvent(response);
            session.sink().tryEmitNext(event);
            sentCounter.increment();
        } finally {
            sendDurationTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    @Scheduled(cron = "${notification.sse.ping-cron}")
    public void evictDeadSessions() {
        ServerSentEvent<Object> ping = ServerSentEvent.builder().comment("ping").build();
        sessions.forEach((memberId, session) -> {
            Sinks.EmitResult result = session.sink().tryEmitNext(ping);
            if (result.isFailure()) {
                sessions.remove(memberId, session);
            }
        });
    }

    @Override
    public boolean isConnected(Long memberId) {
        return sessions.containsKey(memberId);
    }

    @Override
    public List<Channel> getCachedChannels(Long memberId) {
        SseSession session = sessions.get(memberId);
        return session != null ? session.channels() : List.of();
    }

    private ServerSentEvent<Object> buildEvent(NotificationResponse response) {
        return ServerSentEvent.builder()
                .event("notification")
                .data((Object) response)
                .build();
    }

    private record SseSession(Sinks.Many<ServerSentEvent<Object>> sink, Setting setting,
                               List<Channel> channels) {}
}
