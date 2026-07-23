package com.haagendazs.application.dto;

public record PresenceEvent(
        Long memberId,
        String status
) {}
