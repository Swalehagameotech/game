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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Server-authoritative wallet settlement for completed Teen Patti hands.
 * Credits winner payout and platform rake using idempotent ledger reference IDs.
 */
@Slf4j
@Service
public class WalletSettlementService {

    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final WebSocketEventPublisher eventPublisher;
    private final String houseAccountId;

    public WalletSettlementService(
            WalletService walletService,
            WalletRepository walletRepository,
            LedgerEntryRepository ledgerEntryRepository,
            WebSocketEventPublisher eventPublisher,
            @Value("${app.game.house-account-id:house_platform_admin}") String houseAccountId) {
        this.walletService = walletService;
        this.walletRepository = walletRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.eventPublisher = eventPublisher;
        this.houseAccountId = houseAccountId;
    }

    public static String winReferenceId(String tableId, String handId) {
        return "table:" + tableId + ":hand:" + handId + ":win";
    }

    public static String rakeReferenceId(String tableId, String handId) {
        return "table:" + tableId + ":hand:" + handId + ":rake";
    }

    public WalletSettlementResult settleHand(String tableId, String handId, HandOutcome outcome) {
        if (tableId == null || handId == null || outcome == null) {
            throw new IllegalArgumentException("tableId, handId, and outcome must not be null");
        }
        if (outcome.getWinnerId() == null || outcome.getWinnerId().isBlank()) {
            throw new IllegalArgumentException("Winner id must not be null");
        }

        validatePotIntegrity(outcome);

        String winnerId = outcome.getWinnerId();
        long payoutPaise = outcome.getWinnerPayoutPaise();
        long rakePaise = outcome.getRakeAmountPaise();

        String winRef = winReferenceId(tableId, handId);
        String rakeRef = rakeReferenceId(tableId, handId);

        boolean winReplay = ledgerEntryRepository.findByReferenceId(winRef).isPresent();
        boolean rakeReplay = rakePaise > 0 && ledgerEntryRepository.findByReferenceId(rakeRef).isPresent();

        LedgerEntry winEntry = walletService.applyLedgerEntry(winnerId, LedgerEntryType.WIN, payoutPaise, winRef);

        Long houseBalanceAfter = null;
        if (rakePaise > 0) {
            ensureHouseWalletExists();
            LedgerEntry rakeEntry = walletService.applyLedgerEntry(houseAccountId, LedgerEntryType.RAKE, rakePaise, rakeRef);
            houseBalanceAfter = rakeEntry.getBalanceAfterPaise();
        }

        WalletSettlementResult result = WalletSettlementResult.builder()
                .tableId(tableId)
                .handId(handId)
                .winnerUserId(winnerId)
                .potPaise(outcome.getPotAmountPaise())
                .rakePaise(rakePaise)
                .payoutPaise(payoutPaise)
                .winnerBalanceAfterPaise(winEntry.getBalanceAfterPaise())
                .houseBalanceAfterPaise(houseBalanceAfter)
                .winIdempotentReplay(winReplay)
                .rakeIdempotentReplay(rakeReplay)
                .winReferenceId(winRef)
                .rakeReferenceId(rakePaise > 0 ? rakeRef : null)
                .settledAt(Instant.now())
                .build();

        eventPublisher.publishWalletSettled(tableId, result);
        log.info("Wallet settlement complete for table [{}] hand [{}]: winner [{}] credited {} paise, rake {} paise",
                tableId, handId, winnerId, payoutPaise, rakePaise);

        return result;
    }

    public WalletSettlementResult settleFromEvent(HandCompletedEvent event) {
        HandOutcome outcome = new HandOutcome(
                event.getWinnerId(),
                event.getPotAmountPaise(),
                event.getRakeAmountPaise(),
                event.getWinnerPayoutPaise(),
                event.getWinningCategory(),
                java.util.Map.of(),
                event.getNotes() != null ? event.getNotes() : "Hand completed");
        return settleHand(event.getTableId(), event.getHandId(), outcome);
    }

    public boolean isAlreadySettled(String tableId, String handId) {
        return ledgerEntryRepository.findByReferenceId(winReferenceId(tableId, handId)).isPresent();
    }

    private void validatePotIntegrity(HandOutcome outcome) {
        long expectedPot = outcome.getWinnerPayoutPaise() + outcome.getRakeAmountPaise();
        if (expectedPot != outcome.getPotAmountPaise()) {
            log.warn("Pot integrity mismatch: pot {} != payout {} + rake {}",
                    outcome.getPotAmountPaise(), outcome.getWinnerPayoutPaise(), outcome.getRakeAmountPaise());
        }
    }

    private void ensureHouseWalletExists() {
        if (walletRepository.findByUserId(houseAccountId).isEmpty()) {
            walletRepository.save(Wallet.builder()
                    .userId(houseAccountId)
                    .balancePaise(0L)
                    .currency("INR")
                    .build());
            log.info("Initialized platform house wallet for [{}]", houseAccountId);
        }
    }
}
