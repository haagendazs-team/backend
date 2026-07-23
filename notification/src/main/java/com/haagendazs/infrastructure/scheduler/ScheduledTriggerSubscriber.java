package com.haagendazs.infrastructure.scheduler;

import com.haagendazs.application.service.ScheduledEventClaimService;
import com.haagendazs.application.service.ScheduledNotificationProcessor;
import com.haagendazs.domain.repository.EventTypeRepository;
import com.haagendazs.infrastructure.config.NotificationProperties;
import com.haagendazs.infrastructure.config.RedisPubSubConfig;
import com.haagendazs.infrastructure.registry.EventTypeRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTriggerSubscriber {

    private final ScheduledNotificationProcessor scheduledProcessor;
    private final ScheduledEventClaimService claimService;
    private final NotificationProperties properties;
    private final ChannelTopic scheduledTriggerTopic;
    private final ChannelTopic stuckRecoveryTopic;
    private final EventTypeRegistry registry;
    private final EventTypeRepository eventTypeRepository;
    private final ChannelTopic eventTypeRegisteredTopic;

    public void register(ReactiveRedisMessageListenerContainer container) {
        container.receive(scheduledTriggerTopic)
                .subscribe(message -> {
                    log.info("예약 알림 트리거 수신");
                    scheduledProcessor.processDue();
                });

        container.receive(stuckRecoveryTopic)
                .subscribe(message -> {
                    log.info("stuck 복구 트리거 수신");
                    LocalDateTime stuckBefore = LocalDateTime.now().minus(properties.pel().stuckTimeout());
                    claimService.claimStuckEvents(stuckBefore).subscribe();
                });

        container.receive(eventTypeRegisteredTopic)
                .subscribe(message -> {
                    String code = message.getMessage();
                    log.info("이벤트 타입 등록 브로드캐스트 수신 code={}", code);
                    eventTypeRepository.findByCode(code)
                            .doOnNext(def -> {
                                registry.register(def);
                            })
                            .subscribe();
                });
    }
}
