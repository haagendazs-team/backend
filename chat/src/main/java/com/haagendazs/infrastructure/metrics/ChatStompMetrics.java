package com.haagendazs.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatStompMetrics {

    private final MeterRegistry meterRegistry;

    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final Set<String> connectedSessionIds = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Long> connectStartedAtNanos = new ConcurrentHashMap<>();

    private Timer connectDurationTimer;
    private Counter subscribeCounter;
    private Counter disconnectNormalCounter;
    private Counter disconnectAbnormalCounter;
    private Counter heartbeatTimeoutCounter;
    private Counter messageCounter;
    private Timer messageProcessingTimer;

    @PostConstruct
    void initMetrics() {
        Gauge.builder("stomp_active_connections", activeConnections, AtomicInteger::get)
                .description("현재 연결되어 있는 STOMP 세션 수")
                .register(meterRegistry);

        connectDurationTimer = Timer.builder("stomp_connect_duration_seconds")
                .description("STOMP CONNECT 프레임 수신 ~ CONNECTED 응답까지 걸린 시간")
                .publishPercentileHistogram()
                .register(meterRegistry);

        subscribeCounter = Counter.builder("stomp_subscribe_total")
                .description("STOMP SUBSCRIBE 프레임 수신 수")
                .register(meterRegistry);

        disconnectNormalCounter = Counter.builder("stomp_disconnect_total")
                .description("STOMP 세션 종료 수")
                .tag("reason", "normal")
                .register(meterRegistry);

        disconnectAbnormalCounter = Counter.builder("stomp_disconnect_total")
                .description("STOMP 세션 종료 수")
                .tag("reason", "abnormal")
                .register(meterRegistry);

        heartbeatTimeoutCounter = Counter.builder("stomp_heartbeat_timeout_total")
                .description("하트비트 응답 없음으로 강제 종료된 세션 수")
                .register(meterRegistry);

        messageCounter = Counter.builder("stomp_message_total")
                .description("STOMP로 수신한 채팅 메시지 수")
                .register(meterRegistry);

        messageProcessingTimer = Timer.builder("stomp_message_processing_duration_seconds")
                .description("채팅 메시지 저장 + Redis 브로드캐스트까지 걸린 시간")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    /** StompController에서 메시지 하나를 처리하는 동안 호출 — 건수와 소요 시간을 함께 기록한다. */
    public void recordMessage(Runnable task) {
        messageCounter.increment();
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            task.run();
        } finally {
            sample.stop(messageProcessingTimer);
        }
    }

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        String sessionId = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        if (sessionId != null) {
            connectStartedAtNanos.put(sessionId, System.nanoTime());
        }
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        String sessionId = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        if (sessionId != null && connectedSessionIds.add(sessionId)) {
            activeConnections.incrementAndGet();
        }

        Long startedAt = (sessionId != null) ? connectStartedAtNanos.remove(sessionId) : null;
        if (startedAt != null) {
            connectDurationTimer.record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
        }
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        subscribeCounter.increment();
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        connectStartedAtNanos.remove(event.getSessionId());

        if (connectedSessionIds.remove(event.getSessionId())) {
            activeConnections.decrementAndGet();
        }

        CloseStatus status = event.getCloseStatus();
        if (status != null && status.equalsCode(CloseStatus.SESSION_NOT_RELIABLE)) {
            heartbeatTimeoutCounter.increment();
            disconnectAbnormalCounter.increment();
        } else if (status == null || status.equalsCode(CloseStatus.NORMAL)) {
            disconnectNormalCounter.increment();
        } else {
            disconnectAbnormalCounter.increment();
        }
    }
}
