package com.haagendazs.application.service;

import com.haagendazs.domain.model.EventTypeDefinition;
import com.haagendazs.infrastructure.registry.EventTypeRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledNotificationProcessor {

    private final ScheduledEventClaimService claimService;
    private final FanoutService fanoutService;
    private final EventStatusService eventStatusService;
    private final EventTypeRegistry registry;

    @Scheduled(cron = "0 0/5 * * * *")
    public void processDue() {
        claimService.claimDueEvents()
                .flatMap(event -> {
                    EventTypeDefinition definition = registry.getByCode(event.getEventTypeCode())
                            .orElseThrow(() -> new IllegalStateException(
                                    "미등록 이벤트 타입: " + event.getEventTypeCode()));
                    return fanoutService.fanout(event, definition, event.getPayload())
                            .flatMap(failed -> eventStatusService.markEventStatus(event.getId(), failed))
                            .doOnError(e -> log.error("예약 알림 처리 실패 eventId={}", event.getId(), e))
                            .onErrorResume(e -> eventStatusService.markEventStatus(event.getId(), true));
                })
                .subscribe(
                        v -> {},
                        e -> log.error("예약 알림 처리 중 오류", e),
                        () -> log.info("예약 알림 처리 완료")
                );
    }
}
