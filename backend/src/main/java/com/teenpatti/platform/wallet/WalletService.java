package com.teenpatti.platform.wallet;

import com.teenpatti.platform.common.exception.InsufficientBalanceException;
import com.teenpatti.platform.common.exception.OptimisticLockException;
import com.teenpatti.platform.common.response.PageResponse;
import com.teenpatti.platform.transaction.LedgerEntry;
import com.teenpatti.platform.transaction.LedgerEntryType;
import com.teenpatti.platform.transaction.LedgerStatus;
import com.teenpatti.platform.wallet.dto.LedgerEntryResponse;
import com.teenpatti.platform.wallet.dto.WalletBalanceResponse;
import com.teenpatti.platform.websocket.WebSocketEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Core financial service — wallet balance, immutable ledger, mirrored wallet_transactions, real-time broadcast.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_OPTIMISTIC_LOCK_RETRIES = 15;

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Autowired(required = false)
    private WebSocketEventPublisher webSocketEventPublisher;

    public WalletBalanceResponse getBalance(String userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Wallet created = Wallet.builder()
                            .userId(userId)
                            .balancePaise(0L)
                            .currency("INR")
                            .build();
                    return walletRepository.save(created);
                });
        return WalletMapper.toBalanceResponse(wallet);
    }

    public PageResponse<LedgerEntryResponse> getLedgerHistory(String userId, int page, int size) {
        int boundedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int boundedPage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(boundedPage, boundedSize);

        Page<LedgerEntry> ledgerPage = ledgerEntryRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.from(ledgerPage, WalletMapper::toLedgerEntryResponse);
    }

    public Page<LedgerEntry> getLedgerEntries(String userId, Pageable pageable) {
        return ledgerEntryRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public WalletBalanceResponse depositDemoChips(String userId, long amountPaise) {
        long depositAmount = amountPaise > 0 ? amountPaise : 100_000L;
        String referenceId = "demo_deposit:" + userId + ":" + System.currentTimeMillis();
        applyLedgerEntry(userId, LedgerEntryType.DEPOSIT, depositAmount, referenceId);
        log.info("Deposited {} paise demo chips to user [{}]", depositAmount, userId);
        return getBalance(userId);
    }

    public LedgerEntry applyLedgerEntry(String userId, LedgerEntryType type, long amountPaise, String referenceId) {
        validateInputs(userId, type, amountPaise, referenceId);

        Optional<LedgerEntry> existingEntryOpt = ledgerEntryRepository.findByReferenceId(referenceId);
        if (existingEntryOpt.isPresent()) {
            log.info("IDEMPOTENT REPLAY: Reference ID [{}] already processed.", referenceId);
            return existingEntryOpt.get();
        }

        int attempts = 0;
        while (attempts < MAX_OPTIMISTIC_LOCK_RETRIES) {
            attempts++;
            try {
                return executeTransactionalLedgerUpdate(userId, type, amountPaise, referenceId);
            } catch (OptimisticLockingFailureException ex) {
                log.warn("Optimistic lock collision on attempt {}/{} for user [{}]", attempts, MAX_OPTIMISTIC_LOCK_RETRIES, userId);
                if (attempts >= MAX_OPTIMISTIC_LOCK_RETRIES) {
                    throw new OptimisticLockException(
                            "Concurrent update conflict: Failed to update wallet balance after " + MAX_OPTIMISTIC_LOCK_RETRIES + " retries."
                    );
                }
                try {
                    Thread.sleep((long) (Math.random() * 40 + 10));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new OptimisticLockException("Thread interrupted during transaction retry");
                }
            }
        }

        throw new OptimisticLockException("Failed to apply ledger entry due to persistent concurrency collision.");
    }

    @Transactional
    protected LedgerEntry executeTransactionalLedgerUpdate(String userId, LedgerEntryType type, long amountPaise, String referenceId) {
        Optional<LedgerEntry> doubleCheckOpt = ledgerEntryRepository.findByReferenceId(referenceId);
        if (doubleCheckOpt.isPresent()) {
            return doubleCheckOpt.get();
        }

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Data integrity error: Wallet missing for user " + userId));

        long signedAmount = amountPaise < 0 ? amountPaise : (isDebitType(type) ? -amountPaise : amountPaise);
        long currentBalance = wallet.getBalancePaise();
        long newBalance = currentBalance + signedAmount;

        if (newBalance < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient wallet balance. Available: " + currentBalance + " paise, required debit: " + Math.abs(amountPaise) + " paise."
            );
        }

        wallet.setBalancePaise(newBalance);
        walletRepository.save(wallet);

        LedgerEntry entry = LedgerEntry.builder()
                .userId(userId)
                .type(type)
                .amountPaise(signedAmount)
                .balanceAfterPaise(newBalance)
                .referenceId(referenceId)
                .status(LedgerStatus.COMPLETED)
                .createdAt(Instant.now())
                .build();

        LedgerEntry savedEntry = ledgerEntryRepository.save(entry);
        mirrorWalletTransaction(savedEntry);
        publishWalletUpdate(userId, newBalance);

        log.info("Applied ledger [{}] type [{}] user [{}] balance {} paise", referenceId, type, userId, newBalance);
        return savedEntry;
    }

    private void mirrorWalletTransaction(LedgerEntry entry) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("ledgerEntryId", entry.getId());

            WalletTransaction tx = WalletTransaction.builder()
                    .userId(entry.getUserId())
                    .amountPaise(entry.getAmountPaise())
                    .type(entry.getType())
                    .referenceId(entry.getReferenceId())
                    .balanceAfterPaise(entry.getBalanceAfterPaise())
                    .status(entry.getStatus())
                    .metadata(metadata)
                    .createdAt(entry.getCreatedAt())
                    .build();
            walletTransactionRepository.save(tx);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Wallet transaction mirror already exists for reference [{}]", entry.getReferenceId());
        }
    }

    private void publishWalletUpdate(String userId, long balancePaise) {
        if (webSocketEventPublisher != null) {
            webSocketEventPublisher.publishWalletUpdated(userId, balancePaise);
        }
    }

    private void validateInputs(String userId, LedgerEntryType type, long amountPaise, String referenceId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must be non-null and non-empty");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must be a valid LedgerEntryType");
        }
        if (amountPaise == 0) {
            throw new IllegalArgumentException("amountPaise must be a non-zero long value");
        }
        if (referenceId == null || referenceId.isBlank()) {
            throw new IllegalArgumentException("referenceId must be non-null and non-empty");
        }
    }

    private boolean isDebitType(LedgerEntryType type) {
        return type == LedgerEntryType.WITHDRAWAL || type == LedgerEntryType.BET;
    }
}
