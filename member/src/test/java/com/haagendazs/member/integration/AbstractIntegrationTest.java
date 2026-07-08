package com.haagendazs.member.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@EnabledIf("isDockerAvailable")
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("haagendazs")
            .withUsername("test")
            .withPassword("test");

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    protected ObjectMapper objectMapper;

    protected MockMvc mockMvc;

    static boolean isDockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> {
            String jdbcUrl = POSTGRES.getJdbcUrl();
            String separator = jdbcUrl.contains("?") ? "&" : "?";
            return jdbcUrl + separator + "currentSchema=member";
        });
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    protected AuthTokens signupAndLogin(String email, String password, String nickname) throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "nickname": "%s"
                                }
                                """.formatted(email, password, nickname)))
                .andExpect(status().isCreated());

        return login(email, password);
    }

    protected AuthTokens login(String email, String password) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(
                objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("data").toString(),
                AuthTokens.class
        );
    }

    protected record AuthTokens(String accessToken, String refreshToken) {
    }
}
