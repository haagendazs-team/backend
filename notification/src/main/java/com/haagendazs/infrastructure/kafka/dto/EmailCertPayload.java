package com.haagendazs.infrastructure.kafka.dto;

public record EmailCertPayload(
        String email,
        String code
) {}
