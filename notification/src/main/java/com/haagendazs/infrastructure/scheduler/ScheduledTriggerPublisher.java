package com.haagendazs.infrastructure.scheduler;

import com.haagendazs.infrastructure.config.NotificationProperties;
import com.haagendazs.infrastructure.config.RedisPubSubConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTriggerPublisher {

    private static final String LEADER_LOCK_KEY = "notification:scheduler:leader";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final NotificationProperties properties;

    @Scheduled(cron = "${notification.scheduler.reserved-dispatch-cron}")
    public void triggerScheduled() {
        redisTemplate.opsForValue()
                .setIfAbsent(LEADER_LOCK_KEY, "1", properties.scheduler().leaderLockTtl())
                .filter(Boolean.TRUE::equals)
                .flatMap(ignored -> redisTemplate.convertAndSend(RedisPubSubConfig.SCHEDULED_TRIGGER_CHANNEL, "trigger"))
                .doOnSuccess(v -> log.info("예약 알림 트리거 발행"))
                .subscribe();
    }

    @Scheduled(cron = "${notification.scheduler.stuck-recovery-cron}")
    public void triggerStuckRecovery() {
        redisTemplate.opsForValue()
                .setIfAbsent(LEADER_LOCK_KEY, "1", properties.scheduler().leaderLockTtl())
                .filter(Boolean.TRUE::equals)
                .flatMap(ignored -> redisTemplate.convertAndSend(RedisPubSubConfig.STUCK_RECOVERY_CHANNEL, "trigger"))
                .doOnSuccess(v -> log.info("stuck 복구 트리거 발행"))
                .subscribe();
    }
}
