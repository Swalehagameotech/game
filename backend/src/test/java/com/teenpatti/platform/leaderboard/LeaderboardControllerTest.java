package com.teenpatti.platform.leaderboard;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LeaderboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LeaderboardEntryRepository leaderboardEntryRepository;

    @Autowired
    private LeaderboardService leaderboardService;

    private User user1;
    private User user2;
    private String token1;

    @BeforeEach
    void setUp() {
        leaderboardEntryRepository.deleteAll();
        userRepository.deleteAll();

        user1 = userRepository.save(User.builder()
                .email("lb1@example.com")
                .phoneNumber("9000066661")
                .passwordHash("hashed")
                .displayName("ProPattiPlayer")
                .accountStatus(AccountStatus.ACTIVE)
                .role(UserRole.PLAYER)
                .build());

        user2 = userRepository.save(User.builder()
                .email("lb2@example.com")
                .phoneNumber("9000066662")
                .passwordHash("hashed")
                .displayName("HighRoller")
                .accountStatus(AccountStatus.ACTIVE)
                .role(UserRole.PLAYER)
                .build());

        token1 = jwtTokenProvider.generateAccessToken(user1.getId());

        String dailyKey = leaderboardService.resolveWindowKey(LeaderboardWindow.DAILY, Instant.now());

        leaderboardEntryRepository.save(LeaderboardEntry.builder()
                .userId(user1.getId())
                .window(LeaderboardWindow.DAILY)
                .windowKey(dailyKey)
                .handsWon(10)
                .handsPlayed(20)
                .totalWinningsPaise(50_000L)
                .build());

        leaderboardEntryRepository.save(LeaderboardEntry.builder()
                .userId(user2.getId())
                .window(LeaderboardWindow.DAILY)
                .windowKey(dailyKey)
                .handsWon(25)
                .handsPlayed(30)
                .totalWinningsPaise(200_000L)
                .build());
    }

    @Test
    @DisplayName("GET /api/leaderboard returns paginated, sorted entries with public displayName")
    void getLeaderboard_Succeeds() throws Exception {
        mockMvc.perform(get("/api/leaderboard?window=DAILY&metric=WINNINGS")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].displayName").value("HighRoller"))
                .andExpect(jsonPath("$.content[0].totalWinningsPaise").value(200000))
                .andExpect(jsonPath("$.content[1].displayName").value("ProPattiPlayer"))
                .andExpect(jsonPath("$.content[1].totalWinningsPaise").value(50000));
    }

    @Test
    @DisplayName("GET /api/leaderboard/me returns user's current rank position")
    void getMyRank_RankedUser_Succeeds() throws Exception {
        mockMvc.perform(get("/api/leaderboard/me?window=DAILY&metric=WINNINGS")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ranked").value(true))
                .andExpect(jsonPath("$.rank").value(2))
                .andExpect(jsonPath("$.displayName").value("ProPattiPlayer"));
    }

    @Test
    @DisplayName("GET /api/leaderboard/me returns ranked=false for unranked player in empty window")
    void getMyRank_UnrankedUser_ReturnsUnrankedResponse() throws Exception {
        mockMvc.perform(get("/api/leaderboard/me?window=WEEKLY&metric=WINNINGS")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ranked").value(false))
                .andExpect(jsonPath("$.rank").doesNotExist());
    }
}
