package com.teenpatti.platform.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenpatti.platform.auth.JwtTokenProvider;
import com.teenpatti.platform.auth.RefreshTokenRepository;
import com.teenpatti.platform.wallet.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerTest {

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

    private User primaryUser;
    private User secondaryUser;
    private String primaryToken;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();

        primaryUser = userRepository.save(User.builder()
                .email("alice@example.com")
                .phoneNumber("9876543210")
                .passwordHash(passwordEncoder.encode("Password123"))
                .displayName("AlicePlayer")
                .accountStatus(AccountStatus.ACTIVE)
                .kycStatus(KycStatus.NOT_STARTED)
                .role(UserRole.PLAYER)
                .build());

        secondaryUser = userRepository.save(User.builder()
                .email("bob@example.com")
                .phoneNumber("9123456789")
                .passwordHash(passwordEncoder.encode("Password123"))
                .displayName("BobPlayer")
                .accountStatus(AccountStatus.ACTIVE)
                .kycStatus(KycStatus.VERIFIED)
                .role(UserRole.PLAYER)
                .build());

        primaryToken = jwtTokenProvider.generateAccessToken(primaryUser.getId());
    }

    @Test
    @DisplayName("GET /api/users/me returns full profile with masked phone and omits sensitive fields")
    void getMyProfile_Authenticated_Success() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + primaryToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(primaryUser.getId()))
                .andExpect(jsonPath("$.data.email").value("alice@example.com"))
                .andExpect(jsonPath("$.data.phoneNumber").value("XXXXXX3210"))
                .andExpect(jsonPath("$.data.displayName").value("AlicePlayer"))
                .andExpect(jsonPath("$.data.role").value("PLAYER"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/users/me unauthenticated returns 401 Unauthorized")
    void getMyProfile_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/users/{userId}/public returns reduced field set without PII")
    void getPublicProfile_ExistingUser_Success() throws Exception {
        mockMvc.perform(get("/api/users/" + secondaryUser.getId() + "/public")
                        .header("Authorization", "Bearer " + primaryToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(secondaryUser.getId()))
                .andExpect(jsonPath("$.data.displayName").value("BobPlayer"))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.data.kycStatus").doesNotExist())
                .andExpect(jsonPath("$.data.accountStatus").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/users/{userId}/public for non-existent user returns 404 Not Found")
    void getPublicProfile_NonExistentUser_Returns404() throws Exception {
        mockMvc.perform(get("/api/users/60f7b57d60927800155b5555/public")
                        .header("Authorization", "Bearer " + primaryToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /api/users/me updates displayName and avatarUrl successfully")
    void updateProfile_ValidFields_Success() throws Exception {
        String payload = """
                {
                    "displayName": "AliceNewName",
                    "avatarUrl": "https://example.com/avatar.png"
                }
                """;

        mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", "Bearer " + primaryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.displayName").value("AliceNewName"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://example.com/avatar.png"));
    }

    @Test
    @DisplayName("PATCH /api/users/me with invalid displayName returns 400 Bad Request")
    void updateProfile_InvalidDisplayName_Returns400() throws Exception {
        String payload = """
                {
                    "displayName": "ab"
                }
                """;

        mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", "Bearer " + primaryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("PATCH /api/users/me attempting to update role returns 400 Bad Request")
    void updateProfile_DisallowedRoleField_Returns400() throws Exception {
        String payload = """
                {
                    "displayName": "AliceUpdated",
                    "role": "ADMIN"
                }
                """;

        mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", "Bearer " + primaryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("DISALLOWED_FIELD"))
                .andExpect(jsonPath("$.message").value("Field 'role' is not updatable via this endpoint."));
    }
}
