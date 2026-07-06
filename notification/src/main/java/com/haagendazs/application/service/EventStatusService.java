package com.haagendazs.application.service;

import com.haagendazs.domain.model.Event;
import com.haagendazs.domain.model.EventTypeDefinition;
import com.haagendazs.domain.model.NotificationEnvelope;
import com.haagendazs.domain.repository.EventRepository;
import com.haagendazs.infrastructure.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventStatusService {

    private final EventRepository eventRepository;
    private final PayloadParser payloadParser;
    private final NotificationProperties properties;

    public Mono<Event> saveEventWithStreamMessageId(EventTypeDefinition definition,
                                                     String payload, String streamMessageId) {
        return eventRepository.findByStreamMessageId(streamMessageId)
                .switchIfEmpty(Mono.defer(() -> {
                    Event event = buildEvent(definition, payload);
                    event.assignStreamMessageId(streamMessageId);
                    return eventRepository.save(event);
                }));
    }

    public Mono<Void> markEventStatus(Long eventId, boolean anyFailed) {
        return eventRepository.findById(eventId)
                .flatMap(event -> applyStatus(event, anyFailed))
                .doOnError(e -> log.warn("마킹할 이벤트를 찾을 수 없음 eventId={}", eventId))
                .then();
    }

    private Event buildEvent(EventTypeDefinition definition, String payload) {
        NotificationEnvelope envelope = payloadParser.parse(payload);
        Optional<LocalDateTime> scheduledAt = payloadParser.extractScheduledAt(envelope);
        if (scheduledAt.isPresent()) {
            return Event.createScheduled(definition.getCode(), payload, scheduledAt.get());
        }
        return Event.create(definition.getCode(), payload);
    }

    private Mono<Event> applyStatus(Event event, boolean anyFailed) {
        if (anyFailed) {
            boolean exhausted = event.incrementRetryAndCheckExhausted(
                    properties.pel().backoffMinutes().size());
            if (exhausted) {
                event.markPermanentlyFailed();
            } else {
                event.markFailed();
            }
        } else {
            event.markPublished();
        }
        return eventRepository.save(event);
    }
}
