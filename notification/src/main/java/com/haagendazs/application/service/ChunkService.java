package com.haagendazs.application.service;

import com.haagendazs.domain.model.Event;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChunkService {

    private final Dispatcher dispatcher;

    public Mono<Boolean> processChunk(Event event, List<Long> memberIds, String payload) {
        if (memberIds.isEmpty()) {
            return Mono.just(false);
        }
        return Flux.fromIterable(memberIds)
                .flatMap(memberId -> dispatcher.toMember(event, memberId, payload))
                .any(failed -> failed);
    }
}
