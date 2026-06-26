package com.haagendazs.application.service;

import com.haagendazs.domain.model.Event;
import com.haagendazs.domain.model.EventTypeDefinition;
import com.haagendazs.domain.repository.SettingEntryRepository;
import com.haagendazs.infrastructure.config.NotificationProperties;
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

    public Mono<Boolean> fanout(Event event, EventTypeDefinition definition, String payload) {
        if (definition.isSingleTarget()) {
            return fanoutSingleTarget(event, definition, payload);
        }
        return fanoutBroadcast(event, definition, payload);
    }

    public Mono<Void> fanoutBuffered(Event event, EventTypeDefinition definition, String payload,
                                      String streamKey, RecordId recordId) {
        if (definition.isSingleTarget()) {
            return fanoutSingleTargetBuffered(event, definition, payload, streamKey, recordId);
        }
        return fanoutBroadcastBuffered(event, definition, payload, streamKey, recordId);
    }

    private Mono<Boolean> fanoutSingleTarget(Event event, EventTypeDefinition definition, String payload) {
        try {
            Long memberId = payloadParser.extractMemberId(payload, definition);
            return chunkService.processChunk(event, List.of(memberId), payload);
        } catch (Exception e) {
            log.error("{} payload에서 memberId 추출 실패", definition.getCode(), e);
            return Mono.just(true);
        }
    }

    private Mono<Void> fanoutSingleTargetBuffered(Event event, EventTypeDefinition definition,
                                                    String payload, String streamKey, RecordId recordId) {
        try {
            Long memberId = payloadParser.extractMemberId(payload, definition);
            return bufferedChunkService.enqueueChunk(event, List.of(memberId), payload, streamKey, recordId);
        } catch (Exception e) {
            log.error("{} payload에서 memberId 추출 실패 (buffered)", definition.getCode(), e);
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

    private Mono<Void> fanoutBroadcastBuffered(Event event, EventTypeDefinition definition,
                                                 String payload, String streamKey, RecordId recordId) {
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
        int chunkSize = properties.fanout().chunkSize();
        String code = definition.getCode();
        return fetchAllPaged(offset ->
                settingEntryRepository.findMemberIdsByEventTypeCode(code, offset, chunkSize));
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
