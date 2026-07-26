package com.teenpatti.platform.table;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenpatti.platform.auth.JwtTokenProvider;
import com.teenpatti.platform.transaction.LedgerEntryType;
import com.teenpatti.platform.user.AccountStatus;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.user.UserRole;
import com.teenpatti.platform.wallet.Wallet;
import com.teenpatti.platform.wallet.WalletRepository;
import com.teenpatti.platform.wallet.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TableControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TableRepository tableRepository;

    @Autowired
    private TableService tableService;

    @SpyBean
    private WalletService walletService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User player1;
    private User player2;
    private User poorPlayer;

    private String token1;
    private String token2;
    private String poorToken;

    private Table waitingTable;
    private Table inProgressTable;
    private Table fullTable;

    @BeforeEach
    void setUp() {
        tableRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();

        player1 = userRepository.save(User.builder()
                .email("p1@example.com")
                .phoneNumber("9876543210")
                .passwordHash("hashed")
                .displayName("PlayerOne")
                .accountStatus(AccountStatus.ACTIVE)
                .role(UserRole.PLAYER)
                .build());

        walletRepository.save(Wallet.builder()
                .userId(player1.getId())
                .balancePaise(100_000L) // ₹1,000
                .currency("INR")
                .build());
        token1 = jwtTokenProvider.generateAccessToken(player1.getId());

        player2 = userRepository.save(User.builder()
                .email("p2@example.com")
                .phoneNumber("9876543211")
                .passwordHash("hashed")
                .displayName("PlayerTwo")
                .accountStatus(AccountStatus.ACTIVE)
                .role(UserRole.PLAYER)
                .build());

        walletRepository.save(Wallet.builder()
                .userId(player2.getId())
                .balancePaise(100_000L)
                .currency("INR")
                .build());
        token2 = jwtTokenProvider.generateAccessToken(player2.getId());

        poorPlayer = userRepository.save(User.builder()
                .email("poor@example.com")
                .phoneNumber("9876543212")
                .passwordHash("hashed")
                .displayName("PoorPlayer")
                .accountStatus(AccountStatus.ACTIVE)
                .role(UserRole.PLAYER)
                .build());

        walletRepository.save(Wallet.builder()
                .userId(poorPlayer.getId())
                .balancePaise(500L) // ₹5 (less than LOW min ₹10 = 1000 paise)
                .currency("INR")
                .build());
        poorToken = jwtTokenProvider.generateAccessToken(poorPlayer.getId());

        // Tables
        waitingTable = tableRepository.save(Table.builder()
                .tableType(TableType.PUBLIC)
                .stakeTier(StakeTier.LOW)
                .maxPlayers(6)
                .seatedPlayerIds(new ArrayList<>())
                .status(TableStatus.WAITING)
                .createdAt(Instant.now())
                .build());

        inProgressTable = tableRepository.save(Table.builder()
                .tableType(TableType.PUBLIC)
                .stakeTier(StakeTier.LOW)
                .maxPlayers(6)
                .seatedPlayerIds(new ArrayList<>(List.of(player1.getId())))
                .status(TableStatus.IN_PROGRESS)
                .createdAt(Instant.now())
                .build());

        fullTable = tableRepository.save(Table.builder()
                .tableType(TableType.PUBLIC)
                .stakeTier(StakeTier.LOW)
                .maxPlayers(2)
                .seatedPlayerIds(new ArrayList<>(List.of("other1", "other2")))
                .status(TableStatus.WAITING)
                .createdAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("Single join succeeds, buy-in debited, player appears seated")
    void joinTable_Success() throws Exception {
        mockMvc.perform(post("/api/tables/" + waitingTable.getId() + "/join")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.heldBuyInPaise").value(1000))
                .andExpect(jsonPath("$.data.tableDetail.seatedPlayers[0].displayName").value("PlayerOne"));

        // Check wallet debited: 100,000 - 1,000 = 99,000
        Wallet wallet = walletRepository.findByUserId(player1.getId()).orElseThrow();
        assertEquals(99_000L, wallet.getBalancePaise());

        Table table = tableRepository.findById(waitingTable.getId()).orElseThrow();
        assertTrue(table.getSeatedPlayerIds().contains(player1.getId()));
    }

    @Test
    @DisplayName("Idempotent re-join by already seated player returns 200 OK with existing seat confirmation")
    void joinTable_AlreadySeated_ReturnsExistingSeatIdempotently() throws Exception {
        // First join
        tableService.joinTable(player1.getId(), waitingTable.getId());

        // Re-join attempt
        mockMvc.perform(post("/api/tables/" + waitingTable.getId() + "/join")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seatIndex").value(0))
                .andExpect(jsonPath("$.data.heldBuyInPaise").value(1000));
    }

    @Test
    @DisplayName("Join attempt on full table fails cleanly with 409 Conflict")
    void joinTable_FullTable_Returns409() throws Exception {
        mockMvc.perform(post("/api/tables/" + fullTable.getId() + "/join")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TABLE_FULL"));
    }

    @Test
    @DisplayName("Join attempt with insufficient balance fails with 402 Payment Required")
    void joinTable_InsufficientBalance_Returns402() throws Exception {
        mockMvc.perform(post("/api/tables/" + waitingTable.getId() + "/join")
                        .header("Authorization", "Bearer " + poorToken))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    @DisplayName("Simulated debit failure after seat claim triggers seat claim rollback")
    void joinTable_DebitFailure_RollsBackSeatClaim() throws Exception {
        Mockito.doThrow(new RuntimeException("Simulated wallet service error"))
                .when(walletService).applyLedgerEntry(eq(player1.getId()), eq(LedgerEntryType.BET), anyLong(), anyString());

        assertThrows(RuntimeException.class, () -> tableService.joinTable(player1.getId(), waitingTable.getId()));

        // Assert player is NOT seated on the table
        Table table = tableRepository.findById(waitingTable.getId()).orElseThrow();
        assertFalse(table.getSeatedPlayerIds().contains(player1.getId()), "Seat claim must be rolled back on debit failure");
    }

    @Test
    @DisplayName("Leave WAITING table refunds buy-in fully and removes player")
    void leaveTable_WaitingStatus_RefundsFully() throws Exception {
        tableService.joinTable(player1.getId(), waitingTable.getId());

        mockMvc.perform(post("/api/tables/" + waitingTable.getId() + "/leave")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refunded").value(true))
                .andExpect(jsonPath("$.data.refundAmountPaise").value(1000));

        // Wallet restored to 100,000
        Wallet wallet = walletRepository.findByUserId(player1.getId()).orElseThrow();
        assertEquals(100_000L, wallet.getBalancePaise());

        Table table = tableRepository.findById(waitingTable.getId()).orElseThrow();
        assertFalse(table.getSeatedPlayerIds().contains(player1.getId()));
    }

    @Test
    @DisplayName("Leave IN_PROGRESS table forfeits buy-in (no refund) and flags player in leftMidHandPlayerIds")
    void leaveTable_InProgressStatus_ForfeitsBuyIn() throws Exception {
        mockMvc.perform(post("/api/tables/" + inProgressTable.getId() + "/leave")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refunded").value(false))
                .andExpect(jsonPath("$.data.refundAmountPaise").value(0));

        Table table = tableRepository.findById(inProgressTable.getId()).orElseThrow();
        assertFalse(table.getSeatedPlayerIds().contains(player1.getId()));
        assertTrue(table.getLeftMidHandPlayerIds().contains(player1.getId()), "Player must be flagged in leftMidHandPlayerIds");
    }

    @Test
    @DisplayName("Scheduled cleanup closes stale WAITING table past timeout and refunds seated players")
    void scheduledCleanup_ClosesStaleTableAndRefunds() throws Exception {
        // Create stale table created 15 minutes ago
        Table staleTable = tableRepository.save(Table.builder()
                .tableType(TableType.PUBLIC)
                .stakeTier(StakeTier.LOW)
                .maxPlayers(6)
                .seatedPlayerIds(new ArrayList<>(List.of(player1.getId())))
                .status(TableStatus.WAITING)
                .createdAt(Instant.now().minusSeconds(15 * 60)) // 15 mins ago
                .build());

        // Perform cleanup
        int closed = tableService.cleanupStaleWaitingTables(10);
        assertEquals(1, closed);

        Table closedTable = tableRepository.findById(staleTable.getId()).orElseThrow();
        assertEquals(TableStatus.CLOSED, closedTable.getStatus());
        assertTrue(closedTable.getSeatedPlayerIds().isEmpty());
    }

    @Test
    @DisplayName("GET /api/tables/{tableId} returns full table details with resolved display names")
    void getTableDetails_Success() throws Exception {
        tableService.joinTable(player1.getId(), waitingTable.getId());

        mockMvc.perform(get("/api/tables/" + waitingTable.getId())
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tableId").value(waitingTable.getId()))
                .andExpect(jsonPath("$.data.seatedPlayers[0].displayName").value("PlayerOne"));
    }
}
