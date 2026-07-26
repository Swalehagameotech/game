package com.teenpatti.platform.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenpatti.platform.auth.dto.*;
import com.teenpatti.platform.user.AccountStatus;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.wallet.Wallet;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

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

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Register with valid request creates User and zero-balance Wallet atomically")
    void register_Success() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("player1@example.com")
                .phoneNumber("9876543210")
                .password("Password123")
                .displayName("PlayerOne")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.user.email").value("player1@example.com"))
                .andExpect(jsonPath("$.data.user.phoneNumber").value("9876543210"))
                .andExpect(jsonPath("$.data.user.displayName").value("PlayerOne"))
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());

        // Verify User saved in Mongo
        Optional<User> savedUserOpt = userRepository.findByEmail("player1@example.com");
        assertTrue(savedUserOpt.isPresent());
        User savedUser = savedUserOpt.get();
        assertTrue(passwordEncoder.matches("Password123", savedUser.getPasswordHash()));

        // Verify Wallet created atomically with welcome bonus balancePaise = 10000
        Optional<Wallet> walletOpt = walletRepository.findByUserId(savedUser.getId());
        assertTrue(walletOpt.isPresent());
        assertEquals(10_000L, walletOpt.get().getBalancePaise());
    }

    @Test
    @DisplayName("Register with duplicate email fails with 409 Conflict")
    void register_DuplicateEmail_Fails() throws Exception {
        userRepository.save(User.builder()
                .email("duplicate@example.com")
                .phoneNumber("9111111111")
                .passwordHash(passwordEncoder.encode("Password123"))
                .displayName("ExistingUser")
                .accountStatus(AccountStatus.ACTIVE)
                .build());

        RegisterRequest request = RegisterRequest.builder()
                .email("duplicate@example.com")
                .phoneNumber("9222222222")
                .password("Password123")
                .displayName("NewUser")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_USER"));
    }

    @Test
    @DisplayName("Register with duplicate phone fails with 409 Conflict")
    void register_DuplicatePhone_Fails() throws Exception {
        userRepository.save(User.builder()
                .email("original@example.com")
                .phoneNumber("9876543210")
                .passwordHash(passwordEncoder.encode("Password123"))
                .displayName("Original")
                .accountStatus(AccountStatus.ACTIVE)
                .build());

        RegisterRequest request = RegisterRequest.builder()
                .email("other@example.com")
                .phoneNumber("9876543210")
                .password("Password123")
                .displayName("Other")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_USER"));
    }

    @Test
    @DisplayName("Register with weak password fails validation with 400 Bad Request")
    void register_WeakPassword_FailsValidation() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("valid@example.com")
                .phoneNumber("9876543210")
                .password("weak")
                .displayName("ValidUser")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Login with valid credentials returns tokens and user profile")
    void login_Success() throws Exception {
        User user = userRepository.save(User.builder()
                .email("login@example.com")
                .phoneNumber("9876543210")
                .passwordHash(passwordEncoder.encode("Password123"))
                .displayName("LoginUser")
                .accountStatus(AccountStatus.ACTIVE)
                .build());

        LoginRequest loginRequest = LoginRequest.builder()
                .loginId("login@example.com")
                .password("Password123")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.user.id").value(user.getId()));
    }

    @Test
    @DisplayName("Login with wrong password fails with 401 Unauthorized")
    void login_WrongPassword_Fails() throws Exception {
        userRepository.save(User.builder()
                .email("login@example.com")
                .phoneNumber("9876543210")
                .passwordHash(passwordEncoder.encode("Password123"))
                .displayName("LoginUser")
                .accountStatus(AccountStatus.ACTIVE)
                .build());

        LoginRequest loginRequest = LoginRequest.builder()
                .loginId("login@example.com")
                .password("WrongPassword1")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email/phone or password."));
    }

    @Test
    @DisplayName("Login with non-existent email fails with indistinguishable 401 Unauthorized")
    void login_NonExistentUser_Fails() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .loginId("nonexistent@example.com")
                .password("Password123")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email/phone or password."));
    }

    @Test
    @DisplayName("Login for SUSPENDED account fails with 403 Forbidden")
    void login_SuspendedAccount_Fails() throws Exception {
        userRepository.save(User.builder()
                .email("suspended@example.com")
                .phoneNumber("9876543210")
                .passwordHash(passwordEncoder.encode("Password123"))
                .displayName("SuspendedUser")
                .accountStatus(AccountStatus.SUSPENDED)
                .build());

        LoginRequest loginRequest = LoginRequest.builder()
                .loginId("suspended@example.com")
                .password("Password123")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_RESTRICTED"));
    }

    @Test
    @DisplayName("Refresh token rotation issues new access & refresh tokens and revokes old refresh token")
    void refresh_Rotation_Success() throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email("refresh@example.com")
                .phoneNumber("9876543210")
                .password("Password123")
                .displayName("RefreshUser")
                .build();

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseJson = registerResult.getResponse().getContentAsString();
        String initialRefreshToken = objectMapper.readTree(responseJson).path("data").path("refreshToken").asText();

        RefreshRequest refreshRequest = RefreshRequest.builder()
                .refreshToken(initialRefreshToken)
                .build();

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andReturn();

        String newResponseJson = refreshResult.getResponse().getContentAsString();
        String newRefreshToken = objectMapper.readTree(newResponseJson).path("data").path("refreshToken").asText();

        assertNotEquals(initialRefreshToken, newRefreshToken);

        // Attempting to reuse old initial refresh token must fail with 401
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("Logout revokes refresh token and blocks subsequent refresh attempts")
    void logout_RevokesToken() throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email("logout@example.com")
                .phoneNumber("9876543210")
                .password("Password123")
                .displayName("LogoutUser")
                .build();

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseJson = registerResult.getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(responseJson).path("data").path("refreshToken").asText();

        LogoutRequest logoutRequest = LogoutRequest.builder()
                .refreshToken(refreshToken)
                .build();

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Submitting revoked token to /refresh must fail
        RefreshRequest refreshRequest = RefreshRequest.builder()
                .refreshToken(refreshToken)
                .build();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }
}
