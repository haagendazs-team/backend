package com.haagendazs.application.listener;

import com.haagendazs.infrastructure.config.NotificationProperties;
import com.haagendazs.infrastructure.slack.SlackNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FailureEventListener {

    private static final String SUPPRESS_KEY_PREFIX = "notification:slack:suppress:";

    private final SlackNotifier slackNotifier;
    private final NotificationProperties properties;
    private final ReactiveStringRedisTemplate redisTemplate;

    public FailureEventListener(SlackNotifier slackNotifier,
                                 NotificationProperties properties,
                                 ReactiveStringRedisTemplate redisTemplate) {
        this.slackNotifier = slackNotifier;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    @Async
    @EventListener
    public void onPermanentlyFailed(NotificationPermanentlyFailedEvent event) {
        log.error("[알림 영구 실패] eventId={} eventType={} retryCount={} source={}",
                event.eventId(), event.eventTypeCode(), event.retryCount(), event.source());

        if (!isSlackEnabled()) {
            return;
        }
        String key = SUPPRESS_KEY_PREFIX + event.eventTypeCode();
        redisTemplate.opsForValue()
                .setIfAbsent(key, "1", properties.slack().suppressTtl())
                .filter(isNew -> isNew)
                .doOnNext(ignored -> slackNotifier.send(buildMessage(event)))
                .subscribe();
    }

    private boolean isSlackEnabled() {
        String url = properties.slack().webhookUrl();
        return url != null && !url.isBlank();
    }

    private String buildMessage(NotificationPermanentlyFailedEvent event) {
        return String.format(
                "알림 영구 실패\neventId: %d\neventType: %s\nretryCount: %d\nsource: %s\noccurredAt: %s",
                event.eventId(), event.eventTypeCode(), event.retryCount(), event.source(), event.occurredAt()
        );
    }
}
