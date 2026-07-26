package com.teenpatti.platform.wallet;

import com.teenpatti.platform.common.exception.InsufficientBalanceException;
import com.teenpatti.platform.common.exception.OptimisticLockException;
import com.teenpatti.platform.common.response.PageResponse;
import com.teenpatti.platform.transaction.LedgerEntry;
import com.teenpatti.platform.transaction.LedgerEntryType;
import com.teenpatti.platform.transaction.LedgerStatus;
import com.teenpatti.platform.wallet.dto.LedgerEntryResponse;
import com.teenpatti.platform.wallet.dto.WalletBalanceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Core financial service handling wallet balance operations and immutable transaction ledger logging.
 * Guarantees atomicity, idempotency, strict non-negative balance protection, and concurrency safety.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_OPTIMISTIC_LOCK_RETRIES = 15;

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    /**
     * Reads current committed wallet balance for a user.
     */
    public WalletBalanceResponse getBalance(String userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Data integrity error: Wallet missing for user " + userId));
        return WalletMapper.toBalanceResponse(wallet);
    }

    /**
     * Paginated user ledger transaction history, sorted most recent first.
     */
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
        long depositAmount = amountPaise > 0 ? amountPaise : 100_000L; // Default ₹1,000
        String referenceId = "demo_deposit:" + userId + ":" + System.currentTimeMillis();
        applyLedgerEntry(userId, LedgerEntryType.DEPOSIT, depositAmount, referenceId);
        log.info("Deposited {} paise demo chips to user [{}]", depositAmount, userId);
        return getBalance(userId);
    }

    /**
     * Applies a financial transaction to a user's wallet and records an immutable ledger entry.
     *
     * STEP-BY-STEP CONCURRENCY & IDEMPOTENCY FLOW:
     * 1. Check whether a LedgerEntry with this exact referenceId already exists.
     *    If it does, return the existing result WITHOUT reapplying (idempotency guarantee).
     * 2. Start a transactional operation.
     * 3. Read the current Wallet document (including its @Version field).
     * 4. Compute the new balance. If new balance would be negative (< 0), abort & throw InsufficientBalanceException.
     * 5. Update the Wallet document matching userId AND the version read in step 3.
     *    If optimistic locking fails, retry up to MAX_OPTIMISTIC_LOCK_RETRIES times.
     * 6. Insert the new LedgerEntry (status = COMPLETED) as part of the transaction.
     * 7. Commit transaction.
     *
     * @param userId      Target user ID
     * @param type        Ledger entry transaction type
     * @param amountPaise Transaction amount in paise (must be non-zero)
     * @param referenceId Unique reference ID (e.g. deposit:123, match:456:bet)
     * @return Applied or existing LedgerEntry
     */
    public LedgerEntry applyLedgerEntry(String userId, LedgerEntryType type, long amountPaise, String referenceId) {
        validateInputs(userId, type, amountPaise, referenceId);

        // STEP 1: Idempotency Check
        Optional<LedgerEntry> existingEntryOpt = ledgerEntryRepository.findByReferenceId(referenceId);
        if (existingEntryOpt.isPresent()) {
            log.info("IDEMPOTENT REPLAY: Reference ID [{}] already processed. Returning existing ledger entry.", referenceId);
            return existingEntryOpt.get();
        }

        // STEP 2-7: Execute with optimistic locking retries
        int attempts = 0;
        while (attempts < MAX_OPTIMISTIC_LOCK_RETRIES) {
            attempts++;
            try {
                return executeTransactionalLedgerUpdate(userId, type, amountPaise, referenceId);
            } catch (OptimisticLockingFailureException ex) {
                log.warn("Optimistic lock collision on attempt {}/{} for user [{}] and referenceId [{}]",
                        attempts, MAX_OPTIMISTIC_LOCK_RETRIES, userId, referenceId);
                if (attempts >= MAX_OPTIMISTIC_LOCK_RETRIES) {
                    throw new OptimisticLockException(
                            "Concurrent update conflict: Failed to update wallet balance after " + MAX_OPTIMISTIC_LOCK_RETRIES + " retries."
                    );
                }
                // Randomized backoff delay to spread out retry collisions
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
        // Re-verify idempotency inside transaction for race-condition safety
        Optional<LedgerEntry> doubleCheckOpt = ledgerEntryRepository.findByReferenceId(referenceId);
        if (doubleCheckOpt.isPresent()) {
            log.info("IDEMPOTENT REPLAY (in-tx): Reference ID [{}] already processed.", referenceId);
            return doubleCheckOpt.get();
        }

        // STEP 3: Read current Wallet document
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Data integrity error: Wallet missing for user " + userId));

        // STEP 4: Compute new balance and enforce non-negative constraint
        long signedAmount = amountPaise < 0 ? amountPaise : (isDebitType(type) ? -amountPaise : amountPaise);
        long currentBalance = wallet.getBalancePaise();
        long newBalance = currentBalance + signedAmount;

        if (newBalance < 0) {
            log.warn("Debit rejected for user [{}]: Current balance {} paise, attempted debit {} paise",
                    userId, currentBalance, Math.abs(amountPaise));
            throw new InsufficientBalanceException(
                    "Insufficient wallet balance. Available: " + currentBalance + " paise, required debit: " + Math.abs(amountPaise) + " paise."
            );
        }

        // STEP 5: Update Wallet document with optimistic lock version increment
        wallet.setBalancePaise(newBalance);
        walletRepository.save(wallet);

        // STEP 6 & 7: Insert immutable LedgerEntry and commit
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
        log.info("Applied ledger entry [{}] of type [{}] for user [{}]: New balance {} paise",
                referenceId, type, userId, newBalance);

        return savedEntry;
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
        return type == LedgerEntryType.WITHDRAWAL ||
                type == LedgerEntryType.BET;
    }
}
