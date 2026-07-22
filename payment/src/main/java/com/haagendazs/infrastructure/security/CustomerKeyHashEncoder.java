package com.haagendazs.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CustomerKeyHashEncoder {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String secretKey;

    public CustomerKeyHashEncoder(
            @Value("${payment.customer-key.hash-secret}") String secretKey
    ) {
        this.secretKey = secretKey;
    }

    public String encode(String rawCustomerKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );

            mac.init(secretKeySpec);

            byte[] hashBytes = mac.doFinal(
                    rawCustomerKey.getBytes(StandardCharsets.UTF_8)
            );

            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (Exception e) {
            throw new IllegalStateException("customerKey 해시 생성에 실패했습니다.", e);
        }
    }
}
