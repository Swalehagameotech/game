package com.teenpatti.platform.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenpatti.platform.admin.dto.AdminReasonRequest;
import com.teenpatti.platform.admin.dto.ManualAdjustmentRequest;
import com.teenpatti.platform.auth.JwtTokenProvider;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String playerToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        User player = userRepository.save(User.builder()
                .email("player_sec@example.com")
                .phoneNumber("9000022222")
                .passwordHash("hashed")
                .displayName("PlayerSec")
                .accountStatus(AccountStatus.ACTIVE)
                .role(UserRole.PLAYER)
                .build());

        playerToken = jwtTokenProvider.generateAccessToken(player.getId());
    }

    @Test
    @DisplayName("SECURITY TEST: PLAYER-role user receives 403 Forbidden on every single /api/admin/** endpoint")
    void playerRole_Receives403ForbiddenOnAllAdminEndpoints() throws Exception {
        String reasonBody = objectMapper.writeValueAsString(new AdminReasonRequest("Security test"));
        String adjustBody = objectMapper.writeValueAsString(new ManualAdjustmentRequest(1000L, "Goodwill"));

        // Withdrawals
        mockMvc.perform(get("/api/admin/withdrawals").header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/withdrawals/req123/approve").header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/withdrawals/req123/reject").contentType(MediaType.APPLICATION_JSON).content(reasonBody).header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/withdrawals/req123/mark-paid-out").header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isForbidden());

        // KYC
        mockMvc.perform(get("/api/admin/kyc/pending").header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/kyc/user123/approve").header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/kyc/user123/reject").contentType(MediaType.APPLICATION_JSON).content(reasonBody).header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isForbidden());

        // Account management
        mockMvc.perform(post("/api/admin/users/user123/suspend").contentType(MediaType.APPLICATION_JSON).content(reasonBody).header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/users/user123/ban").contentType(MediaType.APPLICATION_JSON).content(reasonBody).header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/users/user123/reinstate").header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isForbidden());

        // Wallet adjustment
        mockMvc.perform(post("/api/admin/wallet/user123/adjust").contentType(MediaType.APPLICATION_JSON).content(adjustBody).header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isForbidden());

        // Dispute Investigation
        mockMvc.perform(get("/api/admin/investigate/user/user123/ledger").header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/investigate/table/table123/matches").header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/investigate/match/match123").header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isForbidden());
    }
}
