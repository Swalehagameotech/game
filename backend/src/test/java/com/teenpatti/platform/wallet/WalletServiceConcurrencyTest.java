package com.teenpatti.platform.wallet;

import com.teenpatti.platform.common.exception.InsufficientBalanceException;
import com.teenpatti.platform.transaction.LedgerEntry;
import com.teenpatti.platform.transaction.LedgerEntryType;
import com.teenpatti.platform.user.AccountStatus;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class WalletServiceConcurrencyTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private Wallet testWallet;

    @BeforeEach
    void setUp() {
        ledgerEntryRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .email("concurrency@example.com")
                .phoneNumber("9999888877")
                .passwordHash("hashedpass")
                .displayName("ConcurrencyUser")
                .accountStatus(AccountStatus.ACTIVE)
                .role(UserRole.PLAYER)
                .build());

        testWallet = walletRepository.save(Wallet.builder()
                .userId(testUser.getId())
                .balancePaise(100_000L) // ₹1,000.00
                .currency("INR")
                .build());
    }

    @Test
    @DisplayName("CONCURRENCY TEST: 50 simultaneous debit calls execute without lost updates or negative balance")
    void concurrencyTest_FiftySimultaneousDebits_BalanceEqualsExpected() throws Exception {
        int numberOfThreads = 50;
        long debitAmountPerThread = 1_000L; // ₹10.00 per thread (Total ₹500.00)
        long expectedFinalBalance = 100_000L - (numberOfThreads * debitAmountPerThread); // 50,000 paise

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            final int threadIndex = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    walletService.applyLedgerEntry(
                            testUser.getId(),
                            LedgerEntryType.BET,
                            debitAmountPerThread,
                            "match:1:hand:1:bet:thread-" + threadIndex
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Thread " + threadIndex + " error: " + e.getMessage());
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Unblock all 50 threads simultaneously
        finishLatch.await(); // Wait for all 50 threads to complete
        executorService.shutdown();

        assertThat(successCount.get()).isEqualTo(numberOfThreads);

        Wallet finalWallet = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        assertThat(finalWallet.getBalancePaise()).isEqualTo(expectedFinalBalance);

        long ledgerCount = ledgerEntryRepository.count();
        assertThat(ledgerCount).isEqualTo(numberOfThreads);
    }

    @Test
    @DisplayName("IDEMPOTENCY TEST: Duplicate referenceId call is a no-op returning original ledger entry")
    void idempotencyTest_DuplicateReferenceId_AppliesOnlyOnce() {
        String referenceId = "deposit:gateway-txn-999";
        long depositAmount = 50_000L; // ₹500.00

        // First application
        LedgerEntry entry1 = walletService.applyLedgerEntry(
                testUser.getId(),
                LedgerEntryType.DEPOSIT,
                depositAmount,
                referenceId
        );

        Wallet walletAfterFirst = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        assertThat(walletAfterFirst.getBalancePaise()).isEqualTo(150_000L);

        // Second application with identical referenceId
        LedgerEntry entry2 = walletService.applyLedgerEntry(
                testUser.getId(),
                LedgerEntryType.DEPOSIT,
                depositAmount,
                referenceId
        );

        Wallet walletAfterSecond = walletRepository.findByUserId(testUser.getId()).orElseThrow();

        assertThat(walletAfterSecond.getBalancePaise()).isEqualTo(150_000L); // Balance unchanged
        assertThat(entry2.getId()).isEqualTo(entry1.getId());
        assertThat(ledgerEntryRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("INSUFFICIENT BALANCE TEST: Debit exceeding current balance is rejected without mutating wallet")
    void insufficientBalanceTest_DebitExceedsBalance_RejectedAndUnchanged() {
        long excessiveDebit = 200_000L; // ₹2,000.00 against ₹1,000.00 balance

        assertThatThrownBy(() -> walletService.applyLedgerEntry(
                testUser.getId(),
                LedgerEntryType.WITHDRAWAL,
                excessiveDebit,
                "withdrawal:req-failed-1"
        )).isInstanceOf(InsufficientBalanceException.class);

        Wallet wallet = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        assertThat(wallet.getBalancePaise()).isEqualTo(100_000L); // Unchanged
        assertThat(ledgerEntryRepository.count()).isEqualTo(0);
    }
}
