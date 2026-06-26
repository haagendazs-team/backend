package com.haagendazs.presentation.controller;

import com.haagendazs.application.service.EventTypeRegistrationService;
import com.haagendazs.presentation.dto.EventTypeResponse;
import com.haagendazs.presentation.dto.RegisterEventTypeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/module/notifications/event-types")
@RequiredArgsConstructor
public class EventTypeController {

    private final EventTypeRegistrationService eventTypeRegistrationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<EventTypeResponse> register(@Valid @RequestBody RegisterEventTypeRequest request) {
        return eventTypeRegistrationService.register(
                        request.code(),
                        request.streamKey(),
                        request.scheduled(),
                        request.singleTarget(),
                        request.memberIdField(),
                        request.scheduledAtField(),
                        request.resolvedOffsetMinutes()
                )
                .map(EventTypeResponse::of);
    }
}
