package com.haagendazs.presentation.controller;

import com.haagendazs.infrastructure.publisher.ReactiveRedisStreamEventPublisher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

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
    public Mono<ResponseEntity<Void>> publish(@Valid @RequestBody PublishRequest request) {
        return publisher.publish(request.eventTypeCode(), request.payload())
                .thenReturn(ResponseEntity.<Void>ok().build());
    }

    @PostMapping("/publish-batch")
    public Mono<ResponseEntity<Void>> publishBatch(@Valid @RequestBody PublishBatchRequest request) {
        return publisher.publish(request.eventTypeCode(), request.payloads())
                .thenReturn(ResponseEntity.<Void>ok().build());
    }

    public record PublishRequest(
            @NotBlank String eventTypeCode,
            @NotNull Map<String, Object> payload
    ) {}

    public record PublishBatchRequest(
            @NotBlank String eventTypeCode,
            @NotNull List<Map<String, Object>> payloads
    ) {}
}
