package com.haagendazs.payment;

import com.haagendazs.payment.global.EnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PaymentApplication {
    public static void main(String[] args) {
        EnvLoader.load();
        SpringApplication.run(PaymentApplication.class, args);
    }
}
