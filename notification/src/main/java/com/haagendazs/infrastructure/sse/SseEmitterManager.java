package com.haagendazs.infrastructure.sse;

import com.haagendazs.application.port.SseNotificationPort;
import com.haagendazs.domain.model.Channel;
import com.haagendazs.domain.model.Setting;
import com.haagendazs.infrastructure.config.NotificationProperties;
import com.haagendazs.presentation.dto.NotificationResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        SseSession session = sessions.get(memberId);
        if (session == null) {
            return;
        }
        long start = System.nanoTime();
        try {
            ServerSentEvent<Object> event = buildEvent(data);
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

    private ServerSentEvent<Object> buildEvent(Object data) {
        if (data instanceof NotificationResponse response) {
            return ServerSentEvent.builder()
                    .event("notification")
                    .data((Object) response)
                    .build();
        }
        return ServerSentEvent.builder().event("notification").data(data).build();
    }

    private record SseSession(Sinks.Many<ServerSentEvent<Object>> sink, Setting setting,
                               List<Channel> channels) {}
}
