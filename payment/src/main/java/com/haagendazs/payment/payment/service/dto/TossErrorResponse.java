package com.haagendazs.payment.payment.service.dto;

public record TossErrorResponse(
        String code,
        String message
) {
}
