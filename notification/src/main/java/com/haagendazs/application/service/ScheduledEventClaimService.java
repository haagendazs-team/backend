package com.haagendazs.application.service;

import com.haagendazs.domain.model.Event;
import com.haagendazs.domain.repository.EventRepository;
import com.haagendazs.infrastructure.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ScheduledEventClaimService {

    private final EventRepository eventRepository;
    private final NotificationProperties properties;

    public Flux<Event> claimDueEvents() {
        return eventRepository.findDueScheduledEvents(LocalDateTime.now(), properties.pel().backoffMinutes().size())
                .flatMap(event -> {
                    event.markProcessing();
                    return eventRepository.save(event);
                });
    }

    public Flux<Event> claimStuckEvents(LocalDateTime stuckBefore) {
        return eventRepository.findStuckProcessingEvents(stuckBefore, properties.pel().maxStuckRetry())
                .flatMap(event -> {
                    event.incrementStuckRetry();
                    event.markPending();
                    return eventRepository.save(event);
                });
    }
}
