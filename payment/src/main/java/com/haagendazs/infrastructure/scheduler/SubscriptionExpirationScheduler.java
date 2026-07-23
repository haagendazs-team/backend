package com.haagendazs.infrastructure.scheduler;

import com.haagendazs.application.service.SubscriptionExpirationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubscriptionExpirationScheduler {

    private final SubscriptionExpirationService subscriptionExpirationService;

    @Scheduled(cron = "0 */5 * * * *")
    public void expireSubscriptions() {
        subscriptionExpirationService.expirePaidSubscriptions();
    }
}
