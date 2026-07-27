package com.teenpatti.platform.game;

import com.teenpatti.platform.auth.JwtTokenProvider;
import com.teenpatti.platform.game.engine.HandOutcome;
import com.teenpatti.platform.support.TestDataFactory;
import com.teenpatti.platform.support.TestDataFactory.TestUserContext;
import com.teenpatti.platform.table.GameVariant;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableRepository;
import com.teenpatti.platform.table.TableStatus;
import com.teenpatti.platform.table.TableType;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.wallet.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.teenpatti.platform.support.TestDataFactory.bearer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GameHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GameHistoryRepository gameHistoryRepository;

    @Autowired
    private GameHistoryService gameHistoryService;

    @Autowired
    private TableRepository tableRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private TestUserContext participant;
    private TestUserContext outsider;
    private String historyId;

    @BeforeEach
    void setUp() {
        gameHistoryRepository.deleteAll();
        tableRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();

        participant = TestDataFactory.createPlayer(
                userRepository, walletRepository, jwtTokenProvider,
                "hist-player@test.com", "HistPlayer", 20_000L);
        outsider = TestDataFactory.createPlayer(
                userRepository, walletRepository, jwtTokenProvider,
                "outsider@test.com", "Outsider", 20_000L);

        Table table = tableRepository.save(Table.builder()
                .tableName("History Test")
                .tableType(TableType.PUBLIC)
                .gameVariant(GameVariant.CLASSIC)
                .bootAmountPaise(1_000L)
                .minPlayers(2)
                .maxPlayers(6)
                .seatedPlayerIds(List.of(participant.user().getId()))
                .status(TableStatus.IN_PROGRESS)
                .build());

        GameHistory saved = gameHistoryService.recordCompletedHand(
                table,
                "hand_hist_1",
                new HandOutcome(participant.user().getId(), 5_000L, 0L, 5_000L, null, java.util.Map.of(), "Fold win"),
                Instant.now());
        historyId = saved.getId();
    }

    @Test
    @DisplayName("GET /api/game/history returns participant history")
    void listHistory_forParticipant() throws Exception {
        mockMvc.perform(get("/api/game/history")
                        .header("Authorization", bearer(participant.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].handId").value("hand_hist_1"))
                .andExpect(jsonPath("$.data.content[0].result").value("WON"));
    }

    @Test
    @DisplayName("GET /api/game/history/{id} denies non-participants")
    void detailHistory_deniesOutsider() throws Exception {
        mockMvc.perform(get("/api/game/history/" + historyId)
                        .header("Authorization", bearer(outsider.accessToken())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/game/history/{id} returns detail for participant")
    void detailHistory_forParticipant() throws Exception {
        mockMvc.perform(get("/api/game/history/" + historyId)
                        .header("Authorization", bearer(participant.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handId").value("hand_hist_1"))
                .andExpect(jsonPath("$.data.result").value("WON"));
    }
}
