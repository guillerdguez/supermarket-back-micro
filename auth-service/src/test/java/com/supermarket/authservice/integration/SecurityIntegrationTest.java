package com.supermarket.authservice.integration;

import com.supermarket.authservice.client.BranchLookupService;
import com.supermarket.authservice.config.TestRedisConfig;
import com.supermarket.authservice.dto.user.UserRequest;
import com.supermarket.authservice.event.AuthEventPublisher;
import com.supermarket.authservice.helper.TestUserHelper;
import com.supermarket.authservice.model.user.UserRole;
import com.supermarket.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static com.supermarket.authservice.fixtures.auth.AuthFixtures.cashierRegisterRequest;
import static com.supermarket.authservice.fixtures.auth.AuthFixtures.userRegisterRequest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import(TestRedisConfig.class)
class SecurityIntegrationTest {
    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.0"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1))
            .withStartupTimeout(Duration.ofSeconds(60));

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestUserHelper testUserHelper;
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @MockitoBean
    private AuthEventPublisher authEventPublisher;
    @MockitoBean
    private BranchLookupService branchLookupService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        Set<String> keys = redisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        given(branchLookupService.branchNames(any())).willReturn(Map.of());
    }

    @Test
    @DisplayName("Access to a protected endpoint without identity headers should return 401")
    void shouldReturn401WhenNoIdentity() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Identity forwarded by the gateway with an insufficient role should return 403")
    void shouldReturn403ForInsufficientRole() throws Exception {
        mockMvc.perform(get("/users")
                        .header("X-User-Id", "10")
                        .header("X-User-Email", "cashier@test.com")
                        .header("X-User-Role", "CASHIER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Login token should be accepted by /auth/validate and expose the caller identity")
    void shouldValidateIssuedToken() throws Exception {
        UserRequest request = userRegisterRequest();
        String token = testUserHelper.registerAndGetToken(request, UserRole.CASHIER);
        Long userId = userRepository.findByEmail(request.getEmail()).orElseThrow().getId();

        mockMvc.perform(get("/auth/validate").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("X-User-Id", String.valueOf(userId)))
                .andExpect(header().string("X-User-Role", "CASHIER"));
    }

    @Test
    @DisplayName("Token from a user deactivated by an admin should be rejected immediately")
    void shouldRejectTokenAfterUserDeactivated() throws Exception {
        UserRequest request = cashierRegisterRequest();
        String token = testUserHelper.registerAndGetToken(request, UserRole.CASHIER);

        mockMvc.perform(get("/auth/validate").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        var user = userRepository.findByEmail(request.getEmail()).orElseThrow();
        user.setActive(false);
        userRepository.save(user);

        mockMvc.perform(get("/auth/validate").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Auth-Error", "Account disabled"));
    }

    @Test
    @DisplayName("Token should be rejected after logout thanks to the Redis blacklist")
    void shouldRejectTokenAfterLogout() throws Exception {
        String token = testUserHelper.registerAndGetToken(userRegisterRequest(), UserRole.CASHIER);

        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/auth/validate").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Auth-Error", "Token invalidated via logout"));
    }
}
