package com.haagendazs.infrastructure.config;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

@Configuration
@ConditionalOnProperty(
        prefix = "payment.seed-data",
        name = "enabled",
        havingValue = "true"
)
public class PaymentSeedDataConfig {

    @Bean
    public ApplicationRunner paymentSeedDataInitializer(DataSource dataSource) {
        return args -> new ResourceDatabasePopulator(
                new ClassPathResource("db/seed-data.sql")
        ).execute(dataSource);
    }
}
