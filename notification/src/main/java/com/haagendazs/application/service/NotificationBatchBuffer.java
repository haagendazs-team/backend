package com.haagendazs.application.service;

import com.haagendazs.domain.model.BufferItem;
import com.haagendazs.infrastructure.config.NotificationProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.haagendazs.infrastructure.config.RedisStreamsConfig.NOTIFICATION_GROUP;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationBatchBuffer {

    private final BulkPersistService bulkPersistService;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final NotificationProperties properties;
    private final MeterRegistry meterRegistry;

    private final ConcurrentLinkedQueue<BufferItem> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger count = new AtomicInteger(0);
    private final ReentrantLock flushLock = new ReentrantLock();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "notif-buffer-flusher")
    );

    private Counter flushTotal;
    private Counter flushErrorTotal;
    private Timer flushDuration;

    @PostConstruct
    void startFlushScheduler() {
        Gauge.builder("buffer_queue_depth", count, AtomicInteger::get)
                .description("NotificationBatchBuffer 대기 항목 수")
                .register(meterRegistry);
        flushTotal = Counter.builder("buffer_flush")
                .description("버퍼 flush 완료 횟수")
                .register(meterRegistry);
        flushErrorTotal = Counter.builder("buffer_flush_error")
                .description("버퍼 flush 실패 횟수")
                .register(meterRegistry);
        flushDuration = Timer.builder("buffer_flush_duration")
                .description("버퍼 flush 1회 소요 시간")
                .register(meterRegistry);
        long intervalMs = properties.buffer().flushInterval().toMillis();
        scheduler.scheduleAtFixedRate(this::flush, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdown();
        flush();
    }

    public void enqueue(BufferItem item) {
        queue.add(item);
        if (count.incrementAndGet() >= properties.buffer().maxSize()) {
            scheduler.execute(this::flush);
        }
    }

    private void flush() {
        if (!flushLock.tryLock()) {
            return;
        }
        long startNs = System.nanoTime();
        try {
            if (queue.isEmpty()) {
                return;
            }
            List<BufferItem> batch = drain();
            if (batch.isEmpty()) {
                return;
            }
            bulkPersistService.persistBuffered(batch).block();
            ackAll(batch);
            flushTotal.increment();
            flushDuration.record(System.nanoTime() - startNs, TimeUnit.NANOSECONDS);
            log.info("버퍼 flush 완료 size={}", batch.size());
        } catch (Exception e) {
            flushErrorTotal.increment();
            log.error("버퍼 flush 실패 size={} — PEL 재처리 대기", queue.size(), e);
        } finally {
            flushLock.unlock();
        }
    }

    private List<BufferItem> drain() {
        List<BufferItem> batch = new ArrayList<>();
        BufferItem item;
        while ((item = queue.poll()) != null) {
            batch.add(item);
        }
        count.set(0);
        return batch;
    }

    private void ackAll(List<BufferItem> batch) {
        batch.stream()
                .collect(Collectors.groupingBy(
                        BufferItem::streamKey,
                        Collectors.mapping(i -> i.recordId().getValue(), Collectors.toList())
                ))
                .forEach((streamKey, ids) ->
                        redisTemplate.opsForStream().acknowledge(
                                streamKey,
                                NOTIFICATION_GROUP,
                                ids.toArray(String[]::new)
                        ).subscribe()
                );
    }
}
