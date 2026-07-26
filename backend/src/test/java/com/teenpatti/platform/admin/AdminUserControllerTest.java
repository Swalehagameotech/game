package com.teenpatti.platform.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenpatti.platform.admin.dto.AdminReasonRequest;
import com.teenpatti.platform.auth.JwtTokenProvider;
import com.teenpatti.platform.auth.RefreshToken;
import com.teenpatti.platform.auth.RefreshTokenRepository;
import com.teenpatti.platform.user.AccountStatus;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AdminActionLogRepository adminActionLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User adminUser;
    private User targetUser;
    private String adminToken;

    @BeforeEach
    void setUp() {
        adminActionLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        adminUser = userRepository.save(User.builder()
                .email("admin_u@example.com")
                .phoneNumber("9000044441")
                .passwordHash("hashed")
                .displayName("AdminU")
                .accountStatus(AccountStatus.ACTIVE)
                .role(UserRole.ADMIN)
                .build());

        adminToken = jwtTokenProvider.generateAccessToken(adminUser.getId());

        targetUser = userRepository.save(User.builder()
                .email("target_u@example.com")
                .phoneNumber("9000044442")
                .passwordHash("hashed")
                .displayName("TargetU")
                .accountStatus(AccountStatus.ACTIVE)
                .role(UserRole.PLAYER)
                .build());

        refreshTokenRepository.save(RefreshToken.builder()
                .userId(targetUser.getId())
                .tokenHash("sample_hash_123")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build());
    }

    @Test
    @DisplayName("Admin suspending user updates accountStatus to SUSPENDED, revokes active refresh tokens, and logs AdminActionLog")
    void suspendUser_RevokesTokensAndSucceeds() throws Exception {
        String reasonBody = objectMapper.writeValueAsString(new AdminReasonRequest("Suspicious activity"));

        mockMvc.perform(post("/api/admin/users/" + targetUser.getId() + "/suspend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reasonBody)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("SUSPENDED"));

        User updated = userRepository.findById(targetUser.getId()).orElseThrow();
        assertEquals(AccountStatus.SUSPENDED, updated.getAccountStatus());
        assertTrue(refreshTokenRepository.findAll().isEmpty(), "Suspended user refresh tokens must be revoked");
        assertEquals(1, adminActionLogRepository.count());
    }

    @Test
    @DisplayName("Admin reinstating suspended user updates accountStatus to ACTIVE")
    void reinstateUser_Succeeds() throws Exception {
        targetUser.setAccountStatus(AccountStatus.SUSPENDED);
        userRepository.save(targetUser);

        mockMvc.perform(post("/api/admin/users/" + targetUser.getId() + "/reinstate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));

        User updated = userRepository.findById(targetUser.getId()).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, updated.getAccountStatus());
        assertEquals(1, adminActionLogRepository.count());
    }
}
