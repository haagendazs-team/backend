package com.haagendazs.payment.payment.service.tools;

import java.security.SecureRandom;

import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
public class TossCustomerKeyGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String generate() {
        byte[] randomBytes = new byte[32]; // 256-bit random
        SECURE_RANDOM.nextBytes(randomBytes);

        String randomPart = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        // 토스 customerKey 조건 보장:
        // 영문: ck, A
        // 숫자: 1
        // 특수문자: _, .
        return "ck_" + randomPart + ".A1";
    }
}
