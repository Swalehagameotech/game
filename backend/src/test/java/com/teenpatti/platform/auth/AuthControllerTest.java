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

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Register with unique username creates User and Wallet")
    void register_Success() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
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
                .andExpect(jsonPath("$.data.user.displayName").value("PlayerOne"))
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());

        Optional<User> savedUserOpt = userRepository.findByDisplayName("PlayerOne");
        assertTrue(savedUserOpt.isPresent());
        User savedUser = savedUserOpt.get();
        assertTrue(passwordEncoder.matches("Password123", savedUser.getPasswordHash()));

        Optional<Wallet> walletOpt = walletRepository.findByUserId(savedUser.getId());
        assertTrue(walletOpt.isPresent());
        assertEquals(0L, walletOpt.get().getBalancePaise());
    }

    @Test
    @DisplayName("Register with duplicate username fails with 409 Conflict")
    void register_DuplicateUsername_Fails() throws Exception {
        userRepository.save(User.builder()
                .passwordHash(passwordEncoder.encode("Password123"))
                .displayName("ExistingUser")
                .accountStatus(AccountStatus.ACTIVE)
                .build());

        RegisterRequest request = RegisterRequest.builder()
                .password("Password123")
                .displayName("ExistingUser")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_USER"));
    }

    @Test
    @DisplayName("Register with short password fails validation with 400 Bad Request")
    void register_WeakPassword_FailsValidation() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .password("abc")
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
    @DisplayName("Login with valid username returns tokens and user profile")
    void login_Success() throws Exception {
        User user = userRepository.save(User.builder()
                .passwordHash(passwordEncoder.encode("Password123"))
                .displayName("LoginUser")
                .accountStatus(AccountStatus.ACTIVE)
                .build());

        LoginRequest loginRequest = LoginRequest.builder()
                .loginId("LoginUser")
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
                .passwordHash(passwordEncoder.encode("Password123"))
                .displayName("LoginUser")
                .accountStatus(AccountStatus.ACTIVE)
                .build());

        LoginRequest loginRequest = LoginRequest.builder()
                .loginId("LoginUser")
                .password("WrongPassword1")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    @Test
    @DisplayName("Login with non-existent username fails with 401 Unauthorized")
    void login_NonExistentUser_Fails() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .loginId("nobody")
                .password("Password123")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    @Test
    @DisplayName("Login for SUSPENDED account fails with 403 Forbidden")
    void login_SuspendedAccount_Fails() throws Exception {
        userRepository.save(User.builder()
                .passwordHash(passwordEncoder.encode("Password123"))
                .displayName("SuspendedUser")
                .accountStatus(AccountStatus.SUSPENDED)
                .build());

        LoginRequest loginRequest = LoginRequest.builder()
                .loginId("SuspendedUser")
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
