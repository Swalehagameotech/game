package com.teenpatti.platform.table;

import com.teenpatti.platform.common.exception.TableFullException;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class TableServiceConcurrencyTest {

    @Autowired
    private TableService tableService;

    @Autowired
    private TableRepository tableRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    private Table targetTable;
    private final List<User> testUsers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        tableRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();

        // Seed 20 test users with 50,000 paise balance each
        testUsers.clear();
        for (int i = 0; i < 20; i++) {
            User user = userRepository.save(User.builder()
                    .email("concurrent_player_" + i + "@example.com")
                    .phoneNumber("90000000" + (i < 10 ? "0" + i : i))
                    .passwordHash("hashed")
                    .displayName("Player_" + i)
                    .accountStatus(AccountStatus.ACTIVE)
                    .role(UserRole.PLAYER)
                    .build());

            walletRepository.save(Wallet.builder()
                    .userId(user.getId())
                    .balancePaise(50_000L)
                    .currency("INR")
                    .build());

            testUsers.add(user);
        }

        // Create a table with maxPlayers = 2, already containing 1 player (1 open seat remaining)
        targetTable = tableRepository.save(Table.builder()
                .tableType(TableType.PUBLIC)
                .stakeTier(StakeTier.LOW) // Min buy-in ₹10 = 1000 paise
                .maxPlayers(2)
                .seatedPlayerIds(new ArrayList<>(List.of("existing_host_user")))
                .status(TableStatus.WAITING)
                .build());
    }

    @Test
    @DisplayName("CONCURRENCY TEST: 20 simultaneous join attempts against 1 remaining seat results in exactly 1 success and 19 failures without over-seating")
    void concurrentJoins_OnlyOneSeatClaimed() throws Exception {
        int numberOfThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (User user : testUsers) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    tableService.joinTable(user.getId(), targetTable.getId());
                    successCount.incrementAndGet();
                } catch (TableFullException ex) {
                    failureCount.incrementAndGet();
                } catch (Exception ex) {
                    failureCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release all threads at once
        boolean completed = finishLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(true, completed, "All threads must complete within timeout");
        assertEquals(1, successCount.get(), "EXACTLY 1 join attempt must succeed");
        assertEquals(19, failureCount.get(), "EXACTLY 19 join attempts must be rejected with TableFullException");

        Table finalTable = tableRepository.findById(targetTable.getId()).orElseThrow();
        assertEquals(2, finalTable.getSeatedPlayerIds().size(), "Table final seated count must equal maxPlayers (2)");
    }
}
