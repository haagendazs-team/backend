package com.haagendazs.infrastructure.scheduler;

import com.haagendazs.application.service.PaymentRetryJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentRetryJobScheduler {

    private final PaymentRetryJobService paymentRetryJobService;

    // 정기 자동결제 재시도 예약 작업을 주기적으로 실행합니다.
    @Scheduled(fixedDelay = 30000)
    public void processPaymentRetryJobs() {
        paymentRetryJobService.processDueRetryJobs();
    }
}
