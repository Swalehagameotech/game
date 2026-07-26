package com.teenpatti.platform.lobby;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenpatti.platform.auth.JwtTokenProvider;
import com.teenpatti.platform.lobby.dto.CreatePrivateTableRequest;
import com.teenpatti.platform.table.*;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LobbyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TableRepository tableRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User richUser;
    private User poorUser;
    private String richToken;
    private String poorToken;

    private Table publicLowTable;
    private Table publicHighTable;
    private Table fullTable;

    @BeforeEach
    void setUp() {
        tableRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();

        richUser = userRepository.save(User.builder()
                .email("rich@example.com")
                .phoneNumber("9999900001")
                .passwordHash("hash")
                .displayName("RichPlayer")
                .accountStatus(AccountStatus.ACTIVE)
                .role(UserRole.PLAYER)
                .build());

        walletRepository.save(Wallet.builder()
                .userId(richUser.getId())
                .balancePaise(100_000L) // ₹1,000
                .currency("INR")
                .build());

        richToken = jwtTokenProvider.generateAccessToken(richUser.getId());

        poorUser = userRepository.save(User.builder()
                .email("poor@example.com")
                .phoneNumber("9999900002")
                .passwordHash("hash")
                .displayName("PoorPlayer")
                .accountStatus(AccountStatus.ACTIVE)
                .role(UserRole.PLAYER)
                .build());

        walletRepository.save(Wallet.builder()
                .userId(poorUser.getId())
                .balancePaise(500L) // ₹5 (insufficient for LOW min ₹10 = 1000 paise)
                .currency("INR")
                .build());

        poorToken = jwtTokenProvider.generateAccessToken(poorUser.getId());

        // Seed Tables
        publicLowTable = tableRepository.save(Table.builder()
                .tableType(TableType.PUBLIC)
                .stakeTier(StakeTier.LOW)
                .maxPlayers(6)
                .seatedPlayerIds(List.of("p1", "p2"))
                .status(TableStatus.WAITING)
                .build());

        publicHighTable = tableRepository.save(Table.builder()
                .tableType(TableType.PUBLIC)
                .stakeTier(StakeTier.HIGH)
                .maxPlayers(6)
                .seatedPlayerIds(List.of("p1"))
                .status(TableStatus.IN_PROGRESS)
                .build());

        fullTable = tableRepository.save(Table.builder()
                .tableType(TableType.PUBLIC)
                .stakeTier(StakeTier.LOW)
                .maxPlayers(2)
                .seatedPlayerIds(List.of("p1", "p2"))
                .status(TableStatus.WAITING)
                .build());
    }

    @Test
    @DisplayName("GET /api/lobby/tables returns public non-full tables and excludes full tables")
    void getPublicTables_ExcludesFullAndPrivateTables() throws Exception {
        mockMvc.perform(get("/api/lobby/tables?page=0&size=10")
                        .header("Authorization", "Bearer " + richToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @DisplayName("GET /api/lobby/tables with stakeTier query param filters correctly")
    void getPublicTables_FiltersByStakeTier() throws Exception {
        mockMvc.perform(get("/api/lobby/tables?stakeTier=HIGH")
                        .header("Authorization", "Bearer " + richToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].tableId").value(publicHighTable.getId()))
                .andExpect(jsonPath("$.data.content[0].stakeTier").value("HIGH"));
    }

    @Test
    @DisplayName("POST /api/lobby/tables/private creates private table with invite code, absent from public listing")
    void createPrivateTable_Success_AbsentFromPublicListing() throws Exception {
        CreatePrivateTableRequest request = CreatePrivateTableRequest.builder()
                .stakeTier(StakeTier.MEDIUM)
                .maxPlayers(4)
                .build();

        String responseJson = mockMvc.perform(post("/api/lobby/tables/private")
                        .header("Authorization", "Bearer " + richToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.inviteCode").exists())
                .andReturn().getResponse().getContentAsString();

        String inviteCode = objectMapper.readTree(responseJson).path("data").path("inviteCode").asText();

        // Verify private table is absent from public listing
        mockMvc.perform(get("/api/lobby/tables")
                        .header("Authorization", "Bearer " + richToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2)); // Only the 2 public tables

        // Verify private table lookup by invite code succeeds
        mockMvc.perform(get("/api/lobby/tables/private/" + inviteCode)
                        .header("Authorization", "Bearer " + richToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tableType").value("PRIVATE"))
                .andExpect(jsonPath("$.data.stakeTier").value("MEDIUM"))
                .andExpect(jsonPath("$.data.maxPlayers").value(4))
                .andExpect(jsonPath("$.data.currentPlayerCount").value(1));
    }

    @Test
    @DisplayName("GET /api/lobby/tables/private/{inviteCode} for invalid code returns 404 Not Found")
    void getPrivateTable_InvalidCode_Returns404() throws Exception {
        mockMvc.perform(get("/api/lobby/tables/private/INVALID99")
                        .header("Authorization", "Bearer " + richToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TABLE_NOT_FOUND"));
    }

    @Test
    @DisplayName("check-eligibility: full table returns eligible=false, reason=TABLE_FULL")
    void checkEligibility_FullTable_ReturnsIneligible() throws Exception {
        mockMvc.perform(post("/api/lobby/tables/" + fullTable.getId() + "/check-eligibility")
                        .header("Authorization", "Bearer " + richToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eligible").value(false))
                .andExpect(jsonPath("$.data.reason").value("TABLE_FULL"));
    }

    @Test
    @DisplayName("check-eligibility: insufficient balance returns eligible=false, reason=INSUFFICIENT_BALANCE with minRequiredPaise")
    void checkEligibility_InsufficientBalance_ReturnsIneligible() throws Exception {
        // Poor user has 500 paise, LOW stake tier requires 1000 paise
        mockMvc.perform(post("/api/lobby/tables/" + publicLowTable.getId() + "/check-eligibility")
                        .header("Authorization", "Bearer " + poorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eligible").value(false))
                .andExpect(jsonPath("$.data.reason").value("INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.data.minRequiredPaise").value(1000))
                .andExpect(jsonPath("$.data.currentBalancePaise").value(500));
    }

    @Test
    @DisplayName("check-eligibility: valid table and sufficient balance returns eligible=true")
    void checkEligibility_ValidConditions_ReturnsEligible() throws Exception {
        mockMvc.perform(post("/api/lobby/tables/" + publicLowTable.getId() + "/check-eligibility")
                        .header("Authorization", "Bearer " + richToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eligible").value(true))
                .andExpect(jsonPath("$.data.minRequiredPaise").value(1000))
                .andExpect(jsonPath("$.data.currentBalancePaise").value(100000));
    }
}
