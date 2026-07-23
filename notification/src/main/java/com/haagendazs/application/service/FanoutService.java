package com.haagendazs.application.service;

import com.haagendazs.domain.model.Event;
import com.haagendazs.domain.model.EventTypeDefinition;
import com.haagendazs.domain.model.NotificationEnvelope;
import com.haagendazs.domain.repository.SettingEntryRepository;
import com.haagendazs.infrastructure.config.NotificationProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FanoutService {

    private final SettingEntryRepository settingEntryRepository;
    private final ChunkService chunkService;
    private final BufferedChunkService bufferedChunkService;
    private final PayloadParser payloadParser;
    private final NotificationProperties properties;
    private final MeterRegistry meterRegistry;

    private Counter fanoutTotal;
    private Counter fanoutErrorTotal;
    private Timer fanoutDuration;

    @PostConstruct
    void initMetrics() {
        fanoutTotal = Counter.builder("fanout")
                .description("팬아웃 처리 완료 수")
                .register(meterRegistry);
        fanoutErrorTotal = Counter.builder("fanout_error")
                .description("팬아웃 처리 실패 수")
                .register(meterRegistry);
        fanoutDuration = Timer.builder("fanout_duration")
                .description("팬아웃 1건 처리 지연")
                .register(meterRegistry);
    }

    public Mono<Boolean> fanout(Event event, EventTypeDefinition definition, String payload) {
        if (definition.isSingleTarget()) {
            return fanoutSingleTarget(event, payload);
        }
        return fanoutBroadcast(event, definition, payload);
    }

    public Mono<Void> fanoutBuffered(Event event, EventTypeDefinition definition, String payload, String streamKey, RecordId recordId) {
        long startNs = System.nanoTime();
        Mono<Void> fanout = definition.isSingleTarget()
                ? fanoutSingleTargetBuffered(event, payload, streamKey, recordId)
                : fanoutBroadcastBuffered(event, definition, payload, streamKey, recordId);
        return fanout.doOnSuccess(v -> {
                    fanoutTotal.increment();
                    fanoutDuration.record(System.nanoTime() - startNs, java.util.concurrent.TimeUnit.NANOSECONDS);
                })
                .doOnError(e -> fanoutErrorTotal.increment());
    }

    private Mono<Boolean> fanoutSingleTarget(Event event, String payload) {
        try {
            NotificationEnvelope envelope = payloadParser.parse(payload);
            Long memberId = payloadParser.extractTargetMemberId(envelope);
            return chunkService.processChunk(event, List.of(memberId), payload);
        } catch (Exception e) {
            log.error("{} payload에서 targetMemberId 추출 실패", event.getEventTypeCode(), e);
            return Mono.just(true);
        }
    }

    private Mono<Void> fanoutSingleTargetBuffered(Event event, String payload, String streamKey, RecordId recordId) {
        try {
            NotificationEnvelope envelope = payloadParser.parse(payload);
            Long memberId = payloadParser.extractTargetMemberId(envelope);
            return bufferedChunkService.enqueueChunk(event, List.of(memberId), payload, streamKey, recordId);
        } catch (Exception e) {
            log.error("{} payload에서 targetMemberId 추출 실패 (buffered)", event.getEventTypeCode(), e);
            return Mono.empty();
        }
    }

    private Mono<Boolean> fanoutBroadcast(Event event, EventTypeDefinition definition, String payload) {
        return fetchAllMemberIds(definition)
                .buffer(properties.fanout().chunkSize())
                .flatMap(chunk -> chunkService.processChunk(event, chunk, payload)
                        .onErrorResume(e -> {
                            log.error("청크 처리 실패 eventType={}", definition.getCode(), e);
                            return Mono.just(true);
                        }))
                .any(failed -> failed);
    }

    private Mono<Void> fanoutBroadcastBuffered(Event event, EventTypeDefinition definition, String payload, String streamKey, RecordId recordId) {
        return fetchAllMemberIds(definition)
                .buffer(properties.fanout().chunkSize())
                .flatMap(chunk -> bufferedChunkService.enqueueChunk(event, chunk, payload, streamKey, recordId)
                        .onErrorResume(e -> {
                            log.error("버퍼 청크 enqueue 실패 eventType={}", definition.getCode(), e);
                            return Mono.empty();
                        }))
                .then();
    }

    private Flux<Long> fetchAllMemberIds(EventTypeDefinition definition) {
        return fetchAllPaged(offset -> settingEntryRepository
                .findMemberIdsByEventTypeCode(definition.getCode(), offset, properties.fanout().chunkSize()));
    }

    private Flux<Long> fetchAllPaged(java.util.function.LongFunction<Flux<Long>> fetcher) {
        return Flux.defer(() -> {
            long[] offset = {0};
            int limit = properties.fanout().chunkSize();
            return Flux.generate(sink -> {
                        sink.next(offset[0]);
                        offset[0] += limit;
                    })
                    .cast(Long.class)
                    .flatMapSequential(off -> fetcher.apply(off).collectList())
                    .takeWhile(list -> !list.isEmpty())
                    .flatMap(Flux::fromIterable);
        });
    }
}
