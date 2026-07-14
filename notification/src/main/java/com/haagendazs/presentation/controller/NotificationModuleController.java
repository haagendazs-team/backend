package com.haagendazs.presentation.controller;

import com.haagendazs.domain.model.NotificationEnvelope;
import com.haagendazs.infrastructure.publisher.ReactiveRedisStreamEventPublisher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * MODULE API — 내부 서비스가 알림을 발행하는 용도.
 * Gateway 라우팅: lb://notification 직접 호출 (StripPrefix 없음).
 */
@RestController
@RequestMapping("/module/notifications")
@RequiredArgsConstructor
public class NotificationModuleController {

    private final ReactiveRedisStreamEventPublisher publisher;

    @PostMapping("/publish")
    public Mono<ResponseEntity<Void>> publish(@Valid @RequestBody @NotNull NotificationEnvelope request) {
        return publisher.publish(request)
                .thenReturn(ResponseEntity.<Void>ok().build());
    }

    @PostMapping("/publish-batch")
    public Mono<ResponseEntity<Void>> publishBatch(@Valid @RequestBody @NotEmpty List<NotificationEnvelope> requests) {
        return publisher.publish(requests)
                .thenReturn(ResponseEntity.<Void>ok().build());
    }
}
