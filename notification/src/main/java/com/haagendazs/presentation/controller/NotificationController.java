package com.haagendazs.presentation.controller;

import com.haagendazs.application.dto.UpdateSettingCommand;
import com.haagendazs.application.service.NotificationService;
import com.haagendazs.application.service.SettingService;
import com.haagendazs.application.service.SsePingAckService;
import com.haagendazs.presentation.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final SettingService notificationSettingService;
    private final SsePingAckService ssePingAckService;

    @GetMapping
    public Flux<NotificationResponse> getNotifications(
            @RequestHeader("X-Member-Id") Long memberId,
            @RequestParam(defaultValue = "0") long offset,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return notificationService.getNotifications(memberId, offset, limit)
                .map(NotificationResponse::from);
    }

    @PatchMapping("/{notificationId}/read")
    public Mono<ResponseEntity<Void>> markRead(
            @RequestHeader("X-Member-Id") Long memberId,
            @PathVariable Long notificationId
    ) {
        return notificationService.markRead(memberId, notificationId)
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }

    @PatchMapping("/read-all")
    public Mono<ResponseEntity<Void>> markAllRead(@RequestHeader("X-Member-Id") Long memberId) {
        return notificationService.markAllRead(memberId)
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> subscribe(
            @RequestHeader("X-Member-Id") Long memberId,
            @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId
    ) {
        return notificationService.subscribe(memberId, lastEventId);
    }

    @PostMapping("/stream/ack")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> ackPing(
            @RequestHeader("X-Member-Id") Long memberId,
            @Valid @RequestBody PingAckRequest request
    ) {
        ssePingAckService.record(memberId, request.status(), request.pingReceivedAt());
        return Mono.empty();
    }

    @GetMapping("/stream/ping-result")
    public Mono<PingResultResponse> getPingResult(@RequestHeader("X-Member-Id") Long memberId) {
        return Mono.just(ssePingAckService.query(memberId));
    }


    @GetMapping("/settings")
    public Flux<SettingResponse> getSettings(@RequestHeader("X-Member-Id") Long memberId) {
        return notificationSettingService.getSettings(memberId)
                .map(SettingResponse::from);
    }

    @PutMapping("/settings")
    public Mono<SettingResponse> updateSetting(
            @RequestHeader("X-Member-Id") Long memberId,
            @Valid @RequestBody UpdateSettingRequest request
    ) {
        return notificationSettingService.updateSetting(
                memberId,
                new UpdateSettingCommand(request.eventTypeCode(), request.enabled())
        ).map(SettingResponse::from);
    }

    @GetMapping("/channels")
    public Flux<ChannelResponse> getChannels(@RequestHeader("X-Member-Id") Long memberId) {
        return notificationSettingService.getChannels(memberId)
                .map(ChannelResponse::from);
    }

    @PostMapping("/channels")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ChannelResponse> registerChannel(
            @RequestHeader("X-Member-Id") Long memberId,
            @Valid @RequestBody RegisterChannelRequest request
    ) {
        return notificationSettingService.registerChannel(memberId, request.channelType(), request.channelTarget())
                .map(ChannelResponse::from);
    }

    @DeleteMapping("/channels/{channelId}")
    public Mono<ResponseEntity<Void>> deleteChannel(
            @RequestHeader("X-Member-Id") Long memberId,
            @PathVariable Long channelId
    ) {
        return notificationSettingService.deleteChannel(memberId, channelId)
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }

    @PatchMapping("/channels/{channelId}/toggle")
    public Mono<ChannelResponse> toggleChannel(
            @RequestHeader("X-Member-Id") Long memberId,
            @PathVariable Long channelId
    ) {
        return notificationSettingService.toggleChannel(memberId, channelId)
                .map(ChannelResponse::from);
    }
}
