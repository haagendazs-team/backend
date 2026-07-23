package com.haagendazs.member.application.dto;

public record TokenResult(
        String accessToken,
        String refreshToken
) {
}
