package com.haagendazs.payment.subscription.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubscriptionRenewalScheduler {

    private final SubscriptionRenewalService subscriptionRenewalService;

    // 만료 시점이 지난 구독을 갱신합니다. STANDARD는 무결제 연장, 유료 플랜은 자동결제를 시도합니다.
    @Scheduled(cron = "0 */5 * * * *")
    public void renewDueSubscriptions() {
        subscriptionRenewalService.renewDueSubscriptions();
    }
}
