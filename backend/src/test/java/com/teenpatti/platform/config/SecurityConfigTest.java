package com.teenpatti.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenpatti.platform.auth.JwtTokenProvider;
import com.teenpatti.platform.auth.RefreshTokenRepository;
import com.teenpatti.platform.auth.dto.LoginRequest;
import com.teenpatti.platform.common.response.ApiResponse;
import com.teenpatti.platform.user.AccountStatus;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.user.UserRole;
import com.teenpatti.platform.wallet.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @TestConfiguration
    static class TestAdminConfig {
        @RestController
        @RequestMapping("/api/admin")
        static class TestAdminController {
            @GetMapping("/test")
            @PreAuthorize("hasRole('ADMIN')")
            public ResponseEntity<ApiResponse<String>> getAdminData() {
                return ResponseEntity.ok(ApiResponse.success("Admin access granted", "ADMIN_DATA"));
            }
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User playerUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();

        playerUser = userRepository.save(User.builder()
                .email("player@example.com")
                .phoneNumber("9876543210")
                .passwordHash(passwordEncoder.encode("Password123"))
                .displayName("PlayerUser")
                .accountStatus(AccountStatus.ACTIVE)
                .role(UserRole.PLAYER)
                .build());

        adminUser = userRepository.save(User.builder()
                .email("admin@example.com")
                .phoneNumber("9111111111")
                .passwordHash(passwordEncoder.encode("AdminPass123"))
                .displayName("AdminUser")
                .accountStatus(AccountStatus.ACTIVE)
                .role(UserRole.ADMIN)
                .build());
    }

    @Test
    @DisplayName("Unauthenticated request to protected endpoint returns 401 Unauthorized")
    void protectedEndpoint_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Protected endpoint rejects invalid Bearer token with 401 Unauthorized")
    void protectedEndpoint_InvalidToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer invalid_token_string"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Protected endpoint rejects REFRESH-type token with 401 Unauthorized")
    void protectedEndpoint_RefreshTokenUsedAsAccessToken_Returns401() throws Exception {
        String refreshToken = jwtTokenProvider.generateRefreshToken(playerUser.getId());

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Protected endpoint accepts valid ACCESS token and resolves user identity")
    void protectedEndpoint_ValidAccessToken_Success() throws Exception {
        String accessToken = jwtTokenProvider.generateAccessToken(playerUser.getId());

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(playerUser.getId()));
    }

    @Test
    @DisplayName("PLAYER role token attempting ADMIN endpoint returns 403 Forbidden")
    void adminEndpoint_PlayerRole_Returns403() throws Exception {
        String playerToken = jwtTokenProvider.generateAccessToken(playerUser.getId());

        mockMvc.perform(get("/api/admin/test")
                        .header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("ADMIN role token accessing ADMIN endpoint succeeds with 200 OK")
    void adminEndpoint_AdminRole_Success() throws Exception {
        String adminToken = jwtTokenProvider.generateAccessToken(adminUser.getId());

        mockMvc.perform(get("/api/admin/test")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("ADMIN_DATA"));
    }

    @Test
    @DisplayName("CORS preflight OPTIONS request succeeds for allowed frontend origin")
    void corsPreflight_AllowedOrigin_Success() throws Exception {
        mockMvc.perform(options("/api/users/me")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    @DisplayName("Rapid repeated login attempts exceed rate limit and return 429 Too Many Requests with Retry-After header")
    void rateLimiting_Exceeded_Returns429() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .loginId("player@example.com")
                .password("WrongPassword")
                .build();

        String jsonPayload = objectMapper.writeValueAsString(request);

        for (int i = 0; i < 15; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonPayload));
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.errorCode").value("TOO_MANY_REQUESTS"));
    }
}
