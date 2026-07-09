package com.haagendazs.payment.subscription.service.dto;

import java.time.LocalDateTime;

public record GetsubscriptionPeriodsResponse(
    String planName,
    LocalDateTime periodStart,
    LocalDateTime periodEnd
) {
}
