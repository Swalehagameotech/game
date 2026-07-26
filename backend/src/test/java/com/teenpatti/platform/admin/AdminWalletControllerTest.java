package com.teenpatti.platform.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenpatti.platform.admin.dto.ManualAdjustmentRequest;
import com.teenpatti.platform.auth.JwtTokenProvider;
import com.teenpatti.platform.user.AccountStatus;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.user.UserRole;
import com.teenpatti.platform.wallet.LedgerEntryRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminWalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private AdminActionLogRepository adminActionLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User adminUser;
    private User playerUser;
    private String adminToken;

    @BeforeEach
    void setUp() {
        adminActionLogRepository.deleteAll();
        ledgerEntryRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();

        adminUser = userRepository.save(User.builder()
                .email("admin_wall@example.com")
                .phoneNumber("9000055551")
                .passwordHash("hashed")
                .displayName("AdminWall")
                .accountStatus(AccountStatus.ACTIVE)
                .role(UserRole.ADMIN)
                .build());

        adminToken = jwtTokenProvider.generateAccessToken(adminUser.getId());

        playerUser = userRepository.save(User.builder()
                .email("player_wall@example.com")
                .phoneNumber("9000055552")
                .passwordHash("hashed")
                .displayName("PlayerWall")
                .accountStatus(AccountStatus.ACTIVE)
                .role(UserRole.PLAYER)
                .build());

        walletRepository.save(Wallet.builder().userId(playerUser.getId()).balancePaise(10_000L).currency("INR").build());
    }

    @Test
    @DisplayName("Manual wallet credit creates ADMIN_ADJUSTMENT LedgerEntry and writes AdminActionLog")
    void adjustWallet_Credit_Succeeds() throws Exception {
        String body = objectMapper.writeValueAsString(new ManualAdjustmentRequest(5000L, "Goodwill support credit"));

        mockMvc.perform(post("/api/admin/wallet/" + playerUser.getId() + "/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ADMIN_ADJUSTMENT"))
                .andExpect(jsonPath("$.amountPaise").value(5000));

        Wallet wallet = walletRepository.findByUserId(playerUser.getId()).orElseThrow();
        assertEquals(15_000L, wallet.getBalancePaise());
        assertEquals(1, adminActionLogRepository.count());
    }

    @Test
    @DisplayName("Manual wallet debit exceeding available balance throws InsufficientBalanceException (400 Bad Request)")
    void adjustWallet_ExcessiveDebit_Fails() throws Exception {
        String body = objectMapper.writeValueAsString(new ManualAdjustmentRequest(-50_000L, "Deduct dispute amount"));

        mockMvc.perform(post("/api/admin/wallet/" + playerUser.getId() + "/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isPaymentRequired());
    }
}
