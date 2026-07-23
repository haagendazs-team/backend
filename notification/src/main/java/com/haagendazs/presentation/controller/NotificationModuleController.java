package com.haagendazs.presentation.controller;

import com.haagendazs.domain.model.NotificationEnvelope;
import com.haagendazs.infrastructure.publisher.ReactiveRedisStreamEventPublisher;
import com.haagendazs.presentation.dto.K6BulkPublishRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.LongStream;

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

    /**
     * k6 전용 Bulk 즉시발송 — startMemberId 부터 count 명에게 eventTypeCode 알림을 한 번에 Stream XADD.
     * DB seed / 토큰 없이 호출 가능한 내부 API.
     */
    @PostMapping("/publish-k6-bulk")
    public Mono<ResponseEntity<Void>> publishK6Bulk(@Valid @RequestBody K6BulkPublishRequest request) {
        List<NotificationEnvelope> envelopes = LongStream
                .range(request.startMemberId(), request.startMemberId() + request.count())
                .mapToObj(memberId -> NotificationEnvelope.of(
                        memberId,
                        NotificationEnvelope.DispatchType.IMMEDIATE,
                        null,
                        buildK6Payload(memberId, request.eventTypeCode(), request.publishedAt())))
                .toList();
        return publisher.publishWithEventTypeCode(envelopes, request.eventTypeCode())
                .thenReturn(ResponseEntity.<Void>ok().build());
    }

    private Object buildK6Payload(long memberId, String eventTypeCode, Long publishedAt) {
        return java.util.Map.of(
                "memberId", memberId,
                "eventTypeCode", eventTypeCode,
                "amount", 10000,
                "publishedAt", publishedAt != null ? publishedAt : System.currentTimeMillis()
        );
    }
}
