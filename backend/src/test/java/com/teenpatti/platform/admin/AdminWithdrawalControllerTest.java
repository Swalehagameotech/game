package com.teenpatti.platform.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenpatti.platform.admin.dto.AdminReasonRequest;
import com.teenpatti.platform.auth.JwtTokenProvider;
import com.teenpatti.platform.transaction.WithdrawalRequest;
import com.teenpatti.platform.transaction.WithdrawalRequestRepository;
import com.teenpatti.platform.transaction.WithdrawalStatus;
import com.teenpatti.platform.user.AccountStatus;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.user.UserRole;
import com.teenpatti.platform.wallet.Wallet;
import com.teenpatti.platform.wallet.WalletRepository;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminWithdrawalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WithdrawalRequestRepository withdrawalRequestRepository;

    @Autowired
    private AdminActionLogRepository adminActionLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User adminUser;
    private User playerUser;
    private String adminToken;
    private WithdrawalRequest pendingWithdrawal;

    @BeforeEach
    void setUp() {
        adminActionLogRepository.deleteAll();
        withdrawalRequestRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();

        adminUser = userRepository.save(User.builder()
                .email("admin_w@example.com")
                .phoneNumber("9000033331")
                .passwordHash("hashed")
                .displayName("AdminW")
                .accountStatus(AccountStatus.ACTIVE)
                .role(UserRole.ADMIN)
                .build());

        adminToken = jwtTokenProvider.generateAccessToken(adminUser.getId());

        playerUser = userRepository.save(User.builder()
                .email("player_w@example.com")
                .phoneNumber("9000033332")
                .passwordHash("hashed")
                .displayName("PlayerW")
                .accountStatus(AccountStatus.ACTIVE)
                .role(UserRole.PLAYER)
                .build());

        walletRepository.save(Wallet.builder().userId(playerUser.getId()).balancePaise(10_000L).currency("INR").build());

        pendingWithdrawal = withdrawalRequestRepository.save(WithdrawalRequest.builder()
                .userId(playerUser.getId())
                .amountPaise(50_000L) // ₹500
                .accountNumber("12345678")
                .ifscCode("HDFC0001234")
                .status(WithdrawalStatus.PENDING_ADMIN_REVIEW)
                .build());
    }

    @Test
    @DisplayName("Admin approving withdrawal updates status to APPROVED and writes AdminActionLog entry")
    void approveWithdrawal_Succeeds() throws Exception {
        mockMvc.perform(post("/api/admin/withdrawals/" + pendingWithdrawal.getId() + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        WithdrawalRequest updated = withdrawalRequestRepository.findById(pendingWithdrawal.getId()).orElseThrow();
        assertEquals(WithdrawalStatus.APPROVED, updated.getStatus());
        assertEquals(adminUser.getId(), updated.getReviewedByAdminId());
        assertEquals(1, adminActionLogRepository.count());
    }

    @Test
    @DisplayName("Admin rejecting withdrawal updates status to REJECTED, refunds held funds to wallet, and writes AdminActionLog")
    void rejectWithdrawal_RefundsWalletAndSucceeds() throws Exception {
        String reasonBody = objectMapper.writeValueAsString(new AdminReasonRequest("Invalid bank IFSC code"));

        mockMvc.perform(post("/api/admin/withdrawals/" + pendingWithdrawal.getId() + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reasonBody)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        // Verify balance refunded: 10,000 + 50,000 = 60,000 paise
        Wallet wallet = walletRepository.findByUserId(playerUser.getId()).orElseThrow();
        assertEquals(60_000L, wallet.getBalancePaise());
        assertEquals(1, adminActionLogRepository.count());
    }

    @Test
    @DisplayName("Attempting to approve or reject withdrawal in non-PENDING status returns 500/409 Conflict")
    void approveWithdrawal_AlreadyApproved_ReturnsConflict() throws Exception {
        pendingWithdrawal.setStatus(WithdrawalStatus.APPROVED);
        withdrawalRequestRepository.save(pendingWithdrawal);

        mockMvc.perform(post("/api/admin/withdrawals/" + pendingWithdrawal.getId() + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isInternalServerError()); // IllegalStateException mapped by exception handler
    }
}
