package com.haagendazs.infrastructure.consumer;

import com.haagendazs.domain.model.EventTypeDefinition;
import com.haagendazs.infrastructure.config.NotificationProperties;
import com.haagendazs.infrastructure.config.RedisStreamsConfig;
import com.haagendazs.infrastructure.registry.EventTypeRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class StreamMaintenanceScheduler implements SmartLifecycle {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final PelMessageProcessor pelMessageProcessor;
    private final NotificationProperties properties;
    private final EventTypeRegistry eventTypeRegistry;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public void start() { running.set(true); }

    @Override
    public void stop() { running.set(false); }

    @Override
    public boolean isRunning() { return running.get(); }

    @Scheduled(cron = "${notification.scheduler.pel-reclaim-cron}")
    public void reclaimPendingMessages() {
        if (!running.get()) {
            return;
        }
        eventTypeRegistry.getAllDefinitions()
                .forEach(definition -> reclaimForStream(definition.getStreamKey(), definition));
    }

    @Scheduled(cron = "${notification.scheduler.stream-trim-cron}")
    public void trimStreams() {
        if (!running.get()) {
            return;
        }
        Flux.fromIterable(eventTypeRegistry.getAllStreamKeys())
                .flatMap(streamKey -> redisTemplate.opsForStream()
                        .trim(streamKey, properties.stream().maxLen(), true)
                        .doOnSuccess(removed -> log.info("Stream trimmed streamKey={} removed={}", streamKey, removed)))
                .subscribe();
    }

    private void reclaimForStream(String streamKey, EventTypeDefinition definition) {
        try {
            int batchSize = properties.pel().batchSize();
            redisTemplate.opsForStream()
                    .pending(streamKey, RedisStreamsConfig.NOTIFICATION_GROUP, Range.unbounded(), batchSize)
                    .flatMapMany(Flux::fromIterable)
                    .filter(this::isBackoffElapsed)
                    .collectList()
                    .flatMapMany(pending -> {
                        if (pending.isEmpty()) {
                            return Flux.empty();
                        }
                        return Flux.fromIterable(groupByBackoff(pending).entrySet())
                                .flatMap(entry -> redisTemplate.opsForStream()
                                        .claim(streamKey,
                                                RedisStreamsConfig.NOTIFICATION_GROUP,
                                                "maintenance-consumer",
                                                entry.getKey(),
                                                entry.getValue().toArray(new RecordId[0]))
                                        .flatMap(message -> pelMessageProcessor.process(streamKey, definition, message)));
                    })
                    .subscribe(
                            v -> {},
                            e -> log.error("PEL 재처리 스케줄러 오류 streamKey={} error={}", streamKey, e.getMessage())
                    );
        } catch (Exception e) {
            log.error("PEL 재처리 스케줄러 오류 streamKey={} error={}", streamKey, e.getMessage());
        }
    }

    private Map<Duration, List<RecordId>> groupByBackoff(List<PendingMessage> pending) {
        return pending.stream()
                .collect(Collectors.groupingBy(
                        msg -> pelMessageProcessor.resolveBackoff((int) msg.getTotalDeliveryCount() - 1),
                        Collectors.mapping(
                                msg -> RecordId.of(msg.getIdAsString()),
                                Collectors.toList()
                        )
                ));
    }

    private boolean isBackoffElapsed(PendingMessage msg) {
        int deliveryCount = (int) msg.getTotalDeliveryCount();
        Duration backoff = pelMessageProcessor.resolveBackoff(deliveryCount - 1);
        return msg.getElapsedTimeSinceLastDelivery().compareTo(backoff) >= 0;
    }
}
