package com.haagendazs.application.dto;

import java.time.LocalDateTime;

public record GetsubscriptionPeriodsResponse(
    String planName,
    LocalDateTime periodStart,
    LocalDateTime periodEnd
) {
}
