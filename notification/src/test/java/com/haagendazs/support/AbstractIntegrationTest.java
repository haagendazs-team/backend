package com.haagendazs.support;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration")
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    private static final int KAFKA_PORT = 19092;

    // apache/kafka:4.0.0 — KRaft 모드, GenericContainer로 직접 구성
    // KafkaContainer는 Confluent entrypoint 스크립트를 실행해 exit code 127 발생
    // 고정 포트(19092) 바인딩으로 ADVERTISED_LISTENERS를 시작 전에 확정
    static final GenericContainer<?> kafka =
            new GenericContainer<>(DockerImageName.parse("apache/kafka:4.0.0"))
                    .withExposedPorts(KAFKA_PORT)
                    .withEnv("KAFKA_NODE_ID", "1")
                    .withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
                    .withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@localhost:9093")
                    .withEnv("KAFKA_LISTENERS", "PLAINTEXT://0.0.0.0:" + KAFKA_PORT + ",CONTROLLER://0.0.0.0:9093")
                    .withEnv("KAFKA_ADVERTISED_LISTENERS", "PLAINTEXT://localhost:" + KAFKA_PORT)
                    .withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER")
                    .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "PLAINTEXT")
                    .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
                    .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
                    .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
                    .withCreateContainerCmdModifier(cmd ->
                            cmd.getHostConfig().withPortBindings(
                                    new PortBinding(Ports.Binding.bindPort(KAFKA_PORT),
                                            new ExposedPort(KAFKA_PORT))))
                    .waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1));

    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"))
                    .withDatabaseName("haagendazs")
                    .withUsername("test")
                    .withPassword("test");

    static final RedisContainer redis =
            new RedisContainer(DockerImageName.parse("redis:8-alpine")
                    .asCompatibleSubstituteFor("redis"));

    static {
        kafka.start();
        postgres.start();
        redis.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:" + KAFKA_PORT);

        registry.add("spring.r2dbc.url", () ->
                "r2dbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432)
                + "/" + postgres.getDatabaseName());
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.r2dbc.properties.schema", () -> "notification");

        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }
}
