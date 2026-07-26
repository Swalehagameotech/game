package com.teenpatti.platform.wallet;

import com.teenpatti.platform.auth.JwtTokenProvider;
import com.teenpatti.platform.transaction.LedgerEntryType;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private WalletService walletService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User testUser;
    private String accessToken;

    @BeforeEach
    void setUp() {
        ledgerEntryRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .email("wallet@example.com")
                .phoneNumber("9876500000")
                .passwordHash("hashed")
                .displayName("WalletUser")
                .accountStatus(AccountStatus.ACTIVE)
                .role(UserRole.PLAYER)
                .build());

        walletRepository.save(Wallet.builder()
                .userId(testUser.getId())
                .balancePaise(25_000L) // ₹250.00
                .currency("INR")
                .build());

        accessToken = jwtTokenProvider.generateAccessToken(testUser.getId());
    }

    @Test
    @DisplayName("GET /api/wallet/balance returns authenticated user's current balance")
    void getBalance_Authenticated_Success() throws Exception {
        mockMvc.perform(get("/api/wallet/balance")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(testUser.getId()))
                .andExpect(jsonPath("$.data.balancePaise").value(25000))
                .andExpect(jsonPath("$.data.currency").value("INR"));
    }

    @Test
    @DisplayName("GET /api/wallet/balance unauthenticated returns 401 Unauthorized")
    void getBalance_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/wallet/balance"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/wallet/ledger returns paginated transaction history")
    void getLedgerHistory_Authenticated_Success() throws Exception {
        // Seed 2 ledger entries
        walletService.applyLedgerEntry(testUser.getId(), LedgerEntryType.DEPOSIT, 10_000L, "deposit:tx-1");
        walletService.applyLedgerEntry(testUser.getId(), LedgerEntryType.BET, 5_000L, "match:1:hand:1:bet:user1");

        mockMvc.perform(get("/api/wallet/ledger?page=0&size=10")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].referenceId").value("match:1:hand:1:bet:user1"))
                .andExpect(jsonPath("$.data.content[1].referenceId").value("deposit:tx-1"))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }
}
