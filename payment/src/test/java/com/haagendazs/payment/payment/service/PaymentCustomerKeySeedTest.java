package com.haagendazs.payment.payment.service;

import com.haagendazs.payment.TestPaymentApplication;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("seed")
@EnabledIfEnvironmentVariable(named = "PAYMENT_CUSTOMER_KEY_SEED", matches = "true")
@SpringBootTest(
        classes = TestPaymentApplication.class,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "eureka.client.enabled=false",
                "spring.kafka.listener.auto-startup=false",
                "spring.jpa.hibernate.ddl-auto=update",
                "spring.flyway.locations=filesystem:src/main/resources/db/migration"
        }
)
class PaymentCustomerKeySeedTest {

    @Autowired
    private PaymentCustomerKeyService paymentCustomerKeyService;

    @Test
    @DisplayName("부하테스트용 customerKey 10개를 DB에 생성한다")
    void seedCustomerKeys() throws IOException {
        int count = intEnv("PAYMENT_CUSTOMER_KEY_SEED_COUNT", 10);
        long startMemberId = longEnv("PAYMENT_CUSTOMER_KEY_SEED_MEMBER_START", 1L);
        List<String> rows = new ArrayList<>();

        rows.add("memberId,customerKey");
        for (int i = 0; i < count; i++) {
            long memberId = startMemberId + i;
            String customerKey = paymentCustomerKeyService.getOrCreateCustomerKey(memberId);
            rows.add(memberId + "," + customerKey);
        }

        String outputPath = env("PAYMENT_CUSTOMER_KEY_SEED_OUTPUT");
        if (!outputPath.isBlank()) {
            Path path = Path.of(outputPath);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(
                    path,
                    rows,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        }

        System.out.println(String.join(System.lineSeparator(), rows));
        assertThat(rows).hasSize(count + 1);
    }

    private String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }

    private int intEnv(String name, int defaultValue) {
        String value = env(name);
        return value.isBlank() ? defaultValue : Integer.parseInt(value);
    }

    private long longEnv(String name, long defaultValue) {
        String value = env(name);
        return value.isBlank() ? defaultValue : Long.parseLong(value);
    }
}
