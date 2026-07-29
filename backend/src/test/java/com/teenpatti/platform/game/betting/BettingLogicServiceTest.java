package com.teenpatti.platform.game.betting;

import com.teenpatti.platform.common.exception.InsufficientBalanceException;
import com.teenpatti.platform.game.engine.BettingRoundEngine;
import com.teenpatti.platform.game.engine.Deck;
import com.teenpatti.platform.game.engine.GameEngineConfig;
import com.teenpatti.platform.game.engine.PlayerAction;
import com.teenpatti.platform.game.engine.PlayerActionType;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.transaction.LedgerEntryType;
import com.teenpatti.platform.wallet.WalletService;
import com.teenpatti.platform.wallet.dto.WalletBalanceResponse;
import com.teenpatti.platform.websocket.WebSocketEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BettingLogicServiceTest {

    private WalletService walletService;
    private WebSocketEventPublisher eventPublisher;
    private BettingLogicService bettingLogicService;
    private BettingRoundEngine engine;
    private Table table;

    @BeforeEach
    void setUp() {
        walletService = mock(WalletService.class);
        eventPublisher = mock(WebSocketEventPublisher.class);
        bettingLogicService = new BettingLogicService(walletService, eventPublisher, mock(com.teenpatti.platform.game.engine.HandContextManager.class));

        GameEngineConfig config = new GameEngineConfig(1000L, 50_000L, 5.0, 2);
        engine = new BettingRoundEngine(config);
        Deck deck = new Deck();
        engine.startHand(List.of("p1", "p2", "p3"), deck);

        table = Table.builder()
                .id("t1")
                .seatedPlayerIds(List.of("p1", "p2", "p3"))
                .build();

        when(walletService.getBalance(anyString())).thenReturn(
                WalletBalanceResponse.builder().userId("p1").balancePaise(100_000L).build(),
                WalletBalanceResponse.builder().userId("p2").balancePaise(100_000L).build(),
                WalletBalanceResponse.builder().userId("p3").balancePaise(100_000L).build()
        );
    }

    @Test
    @DisplayName("Blind player required bet equals current base stake")
    void buildBettingState_blindRequiredBet() {
        BettingState state = bettingLogicService.buildBettingState(table, engine, "p1");

        assertEquals(1000L, state.getRequiredBetPaise());
        assertEquals(2000L, state.getMinRaiseBetPaise());
        assertTrue(state.getAllowedActions().contains("BLIND"));
        assertTrue(state.getAllowedActions().contains("SEE_CARDS"));
        assertFalse(state.getAllowedActions().contains("CHAAL"));
        assertTrue(state.isMyTurn());
    }

    @Test
    @DisplayName("Seen player required bet is blindSeenRatio times base stake")
    void buildBettingState_seenRequiredBet() {
        engine.applyAction(PlayerAction.of("p1", PlayerActionType.SEE_CARDS));

        BettingState state = bettingLogicService.buildBettingState(table, engine, "p1");

        assertEquals(2000L, state.getRequiredBetPaise());
        assertFalse(state.getAllowedActions().contains("SEE_CARDS"));
    }

    @Test
    @DisplayName("resolveBetAmount uses server minimum when client sends low amount")
    void resolveBetAmount_enforcesMinimum() {
        long resolved = bettingLogicService.resolveBetAmount("CHAAL", engine, "p1", 500L);
        assertEquals(1000L, resolved);
    }

    @Test
    @DisplayName("CALL alias resolves same as CHAAL")
    void resolveBetAmount_callAlias() {
        long chaal = bettingLogicService.resolveBetAmount("CHAAL", engine, "p1", 0L);
        long call = bettingLogicService.resolveBetAmount("CALL", engine, "p1", 0L);
        assertEquals(chaal, call);
    }

    @Test
    @DisplayName("validateBalance rejects insufficient wallet funds")
    void validateBalance_insufficientFunds() {
        when(walletService.getBalance("p1")).thenReturn(
                WalletBalanceResponse.builder().userId("p1").balancePaise(100L).build());

        assertThrows(InsufficientBalanceException.class,
                () -> bettingLogicService.validateBalance("p1", 1000L));
    }

    @Test
    @DisplayName("debitBet writes ledger entry and broadcasts wallet update")
    void debitBet_updatesWallet() {
        when(walletService.getBalance("p1")).thenReturn(
                WalletBalanceResponse.builder().userId("p1").balancePaise(100_000L).build(),
                WalletBalanceResponse.builder().userId("p1").balancePaise(99_000L).build());

        bettingLogicService.debitBet("t1", "hand1", "p1", 1000L);

        verify(walletService).applyLedgerEntry(eq("p1"), eq(LedgerEntryType.BET), eq(1000L), anyString());
        verify(eventPublisher).publishWalletUpdated("p1", 99_000L);
    }

    @Test
    @DisplayName("Packed player has no allowed betting actions")
    void resolveAllowedActions_packedPlayerEmpty() {
        engine.applyAction(PlayerAction.of("p1", PlayerActionType.PACK));

        List<String> actions = bettingLogicService.resolveAllowedActions(engine, "p1");

        assertTrue(actions.isEmpty());
    }

    @Test
    @DisplayName("Blind player can See Cards even when it is not their turn")
    void resolveAllowedActions_blindOffTurnCanSeeCards() {
        // p1 is on turn; p2 is BLIND but waiting
        List<String> p2Actions = bettingLogicService.resolveAllowedActions(engine, "p2");

        assertTrue(p2Actions.contains("SEE_CARDS"));
        assertFalse(p2Actions.contains("BLIND"));
        assertFalse(p2Actions.contains("CHAAL"));
    }

    @Test
    void resolveAllowedActions_showOnlyWithTwoPlayers() {
        engine.applyAction(PlayerAction.of("p1", PlayerActionType.PACK));
        engine.applyAction(PlayerAction.of("p2", PlayerActionType.PACK));

        List<String> p3Actions = bettingLogicService.resolveAllowedActions(engine, "p3");
        assertFalse(p3Actions.contains("SHOW"));
    }
}
