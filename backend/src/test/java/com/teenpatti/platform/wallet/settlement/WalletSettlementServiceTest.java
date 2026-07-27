package com.teenpatti.platform.wallet.settlement;

import com.teenpatti.platform.common.event.HandCompletedEvent;
import com.teenpatti.platform.game.engine.HandOutcome;
import com.teenpatti.platform.transaction.LedgerEntry;
import com.teenpatti.platform.transaction.LedgerEntryType;
import com.teenpatti.platform.wallet.LedgerEntryRepository;
import com.teenpatti.platform.wallet.Wallet;
import com.teenpatti.platform.wallet.WalletRepository;
import com.teenpatti.platform.wallet.WalletService;
import com.teenpatti.platform.websocket.WebSocketEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WalletSettlementServiceTest {

    private WalletService walletService;
    private WalletRepository walletRepository;
    private LedgerEntryRepository ledgerEntryRepository;
    private WebSocketEventPublisher eventPublisher;
    private WalletSettlementService walletSettlementService;

    @BeforeEach
    void setUp() {
        walletService = mock(WalletService.class);
        walletRepository = mock(WalletRepository.class);
        ledgerEntryRepository = mock(LedgerEntryRepository.class);
        eventPublisher = mock(WebSocketEventPublisher.class);
        walletSettlementService = new WalletSettlementService(
                walletService, walletRepository, ledgerEntryRepository, eventPublisher, "house_platform_admin");
    }

    @Test
    @DisplayName("settleHand credits winner and rake with idempotent reference IDs")
    void settleHand_creditsWinnerAndRake() {
        HandOutcome outcome = new HandOutcome(
                "winner1",
                10_000L,
                500L,
                9_500L,
                null,
                java.util.Map.of(),
                "Fold win");

        when(ledgerEntryRepository.findByReferenceId("table:t1:hand:h1:win")).thenReturn(Optional.empty());
        when(ledgerEntryRepository.findByReferenceId("table:t1:hand:h1:rake")).thenReturn(Optional.empty());
        when(walletRepository.findByUserId("house_platform_admin")).thenReturn(Optional.of(
                Wallet.builder().userId("house_platform_admin").balancePaise(1000L).build()));
        when(walletService.applyLedgerEntry(eq("winner1"), eq(LedgerEntryType.WIN), eq(9500L), anyString()))
                .thenReturn(LedgerEntry.builder().userId("winner1").balanceAfterPaise(50_000L).build());
        when(walletService.applyLedgerEntry(eq("house_platform_admin"), eq(LedgerEntryType.RAKE), eq(500L), anyString()))
                .thenReturn(LedgerEntry.builder().userId("house_platform_admin").balanceAfterPaise(1500L).build());

        WalletSettlementResult result = walletSettlementService.settleHand("t1", "h1", outcome);

        assertEquals("winner1", result.getWinnerUserId());
        assertEquals(9_500L, result.getPayoutPaise());
        assertEquals(500L, result.getRakePaise());
        assertEquals(50_000L, result.getWinnerBalanceAfterPaise());
        assertEquals(1500L, result.getHouseBalanceAfterPaise());
        assertFalse(result.isWinIdempotentReplay());
        verify(eventPublisher).publishWalletSettled(eq("t1"), eq(result));
    }

    @Test
    @DisplayName("settleHand skips rake when rake amount is zero")
    void settleHand_noRakeWhenZero() {
        HandOutcome outcome = new HandOutcome(
                "winner1",
                5_000L,
                0L,
                5_000L,
                null,
                java.util.Map.of(),
                "No rake hand");

        when(ledgerEntryRepository.findByReferenceId(anyString())).thenReturn(Optional.empty());
        when(walletService.applyLedgerEntry(eq("winner1"), eq(LedgerEntryType.WIN), eq(5000L), anyString()))
                .thenReturn(LedgerEntry.builder().balanceAfterPaise(25_000L).build());

        WalletSettlementResult result = walletSettlementService.settleHand("t1", "h2", outcome);

        assertNull(result.getRakeReferenceId());
        assertNull(result.getHouseBalanceAfterPaise());
        verify(walletService, never()).applyLedgerEntry(eq("house_platform_admin"), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("settleHand detects idempotent replay from existing ledger reference")
    void settleHand_idempotentReplay() {
        HandOutcome outcome = new HandOutcome(
                "winner1",
                3_000L,
                150L,
                2_850L,
                null,
                java.util.Map.of(),
                "Replay");

        when(ledgerEntryRepository.findByReferenceId("table:t1:hand:h3:win"))
                .thenReturn(Optional.of(LedgerEntry.builder().referenceId("table:t1:hand:h3:win").build()));
        when(ledgerEntryRepository.findByReferenceId("table:t1:hand:h3:rake")).thenReturn(Optional.empty());
        when(walletRepository.findByUserId("house_platform_admin")).thenReturn(Optional.of(
                Wallet.builder().userId("house_platform_admin").balancePaise(0L).build()));
        when(walletService.applyLedgerEntry(anyString(), any(), anyLong(), anyString()))
                .thenReturn(LedgerEntry.builder().balanceAfterPaise(10_000L).build());

        WalletSettlementResult result = walletSettlementService.settleHand("t1", "h3", outcome);

        assertTrue(result.isWinIdempotentReplay());
    }

    @Test
    @DisplayName("settleFromEvent delegates to settleHand using event payload")
    void settleFromEvent() {
        HandCompletedEvent event = HandCompletedEvent.builder()
                .tableId("t9")
                .handId("h9")
                .winnerId("w9")
                .potAmountPaise(2000L)
                .rakeAmountPaise(100L)
                .winnerPayoutPaise(1900L)
                .build();

        when(ledgerEntryRepository.findByReferenceId(anyString())).thenReturn(Optional.empty());
        when(walletRepository.findByUserId("house_platform_admin")).thenReturn(Optional.of(
                Wallet.builder().userId("house_platform_admin").balancePaise(0L).build()));
        when(walletService.applyLedgerEntry(anyString(), any(), anyLong(), anyString()))
                .thenReturn(LedgerEntry.builder().balanceAfterPaise(5000L).build());

        WalletSettlementResult result = walletSettlementService.settleFromEvent(event);

        assertEquals("t9", result.getTableId());
        assertEquals("h9", result.getHandId());
        assertEquals(1900L, result.getPayoutPaise());
    }

    @Test
    @DisplayName("Reference ID helpers are stable for idempotency")
    void referenceIds_stable() {
        assertEquals("table:t1:hand:h1:win", WalletSettlementService.winReferenceId("t1", "h1"));
        assertEquals("table:t1:hand:h1:rake", WalletSettlementService.rakeReferenceId("t1", "h1"));
    }
}
