package com.haagendazs.member.infrastructure.kafka;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class EmailVerificationCodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    public String generate() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
