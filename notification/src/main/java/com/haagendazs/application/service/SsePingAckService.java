package com.haagendazs.application.service;

import com.haagendazs.domain.model.PingStatus;
import com.haagendazs.presentation.dto.PingResultResponse;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class SsePingAckService {

    static final long WINDOW_MS = 60_000L;

    private final MeterRegistry meterRegistry;

    private final Map<Long, Deque<PingEntry>> store = new ConcurrentHashMap<>();

    @PostConstruct
    void initMetrics() {
        for (PingStatus status : PingStatus.values()) {
            meterRegistry.counter("sse_notification_result", "status", status.name());
        }
        // 스크레이프 시점마다 현재 윈도우 집계를 Prometheus에 노출
        Gauge.builder("sse_ping_window_total", this, SsePingAckService::computeWindowTotal)
                .description("현재 1분 윈도우의 전체 ping 수신 수 (성공+유실)")
                .register(meterRegistry);
        Gauge.builder("sse_ping_window_success", this, SsePingAckService::computeWindowSuccess)
                .description("현재 1분 윈도우의 성공 ping 수신 수")
                .register(meterRegistry);
    }

    public void record(Long memberId, PingStatus status, LocalDateTime receivedAt) {
        meterRegistry.counter("sse_notification_result", "status", status.name()).increment();
        if (status == PingStatus.실패) {
            return;
        }
        Deque<PingEntry> deque = store.computeIfAbsent(memberId, id -> new ArrayDeque<>());
        synchronized (deque) {
            evictExpired(deque);
            deque.addLast(new PingEntry(receivedAt, status));
        }
    }

    public PingResultResponse query(Long memberId) {
        Deque<PingEntry> deque = store.getOrDefault(memberId, new ArrayDeque<>());
        List<PingEntry> snapshot;
        synchronized (deque) {
            evictExpired(deque);
            snapshot = new ArrayList<>(deque);
        }
        int total = snapshot.size();
        int success = (int) snapshot.stream().filter(e -> e.status() == PingStatus.성공).count();
        List<PingResultResponse.PingEntry> entries = snapshot.stream()
                .map(e -> new PingResultResponse.PingEntry(e.receivedAt(), e.status()))
                .toList();
        return new PingResultResponse(total, success, entries);
    }

    // Gauge 콜백 — Prometheus 스크레이프마다 호출됨 (5초/15초/30초 모두 반영)
    double computeWindowTotal() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        return store.values().stream()
                .mapToLong(deque -> {
                    synchronized (deque) {
                        return deque.stream().filter(e -> e.timestampMs() >= cutoff).count();
                    }
                })
                .sum();
    }

    double computeWindowSuccess() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        return store.values().stream()
                .mapToLong(deque -> {
                    synchronized (deque) {
                        return deque.stream()
                                .filter(e -> e.timestampMs() >= cutoff && e.status() == PingStatus.성공)
                                .count();
                    }
                })
                .sum();
    }

    private void evictExpired(Deque<PingEntry> deque) {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        while (!deque.isEmpty() && deque.peekFirst().timestampMs() < cutoff) {
            deque.pollFirst();
        }
    }

    private record PingEntry(LocalDateTime receivedAt, PingStatus status, long timestampMs) {
        PingEntry(LocalDateTime receivedAt, PingStatus status) {
            this(receivedAt, status, System.currentTimeMillis());
        }
    }
}
