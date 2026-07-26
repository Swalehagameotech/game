package com.teenpatti.platform.game.engine;

import com.teenpatti.platform.game.HandSummary;
import com.teenpatti.platform.game.MatchHistory;
import com.teenpatti.platform.game.MatchHistoryRepository;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableRepository;
import com.teenpatti.platform.table.TableStatus;
import com.teenpatti.platform.transaction.LedgerEntryType;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.wallet.WalletService;
import com.teenpatti.platform.websocket.WebSocketEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Server-authoritative central game loop orchestrator managing Teen Patti table lifecycles:
 * 10s start countdowns, boot collection, SecureRandom deck dealing, 20s turn timers with auto-pack,
 * side-shows, show hand evaluations, winner pot settlement, user matchesPlayedCount incrementing,
 * and automatic 5s next round restarts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameLoopOrchestrator {

    private final TableRepository tableRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final MatchHistoryRepository matchHistoryRepository;
    private final WebSocketEventPublisher eventPublisher;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);
    private final ConcurrentHashMap<String, ScheduledFuture<?>> activeTurnTimers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> activeCountdowns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BettingRoundEngine> activeEngines = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> dealerSeats = new ConcurrentHashMap<>();

    private static final int COUNTDOWN_SECONDS = 10;
    private static final int TURN_TIMEOUT_SECONDS = 20;
    private static final int NEXT_ROUND_DELAY_SECONDS = 5;

    public void handlePlayerSeated(String tableId, int currentCount, int minRequired) {
        Optional<Table> opt = tableRepository.findById(tableId);
        if (opt.isEmpty()) return;
        Table table = opt.get();

        if (table.getStatus() == TableStatus.WAITING && currentCount >= minRequired) {
            if (!activeCountdowns.containsKey(tableId)) {
                log.info("Minimum required players ({}) reached on table [{}]. Starting {}s countdown.", currentCount, tableId, COUNTDOWN_SECONDS);
                table.setStatus(TableStatus.COUNTDOWN);
                table.setUpdatedAt(Instant.now());
                tableRepository.save(table);

                eventPublisher.publishCountdownStarted(tableId, COUNTDOWN_SECONDS);
                eventPublisher.publishTableUpdated(tableId, table);

                ScheduledFuture<?> countdownTask = scheduler.schedule(() -> {
                    activeCountdowns.remove(tableId);
                    startNewRound(tableId);
                }, COUNTDOWN_SECONDS, TimeUnit.SECONDS);

                activeCountdowns.put(tableId, countdownTask);
            }
        }
    }

    public void handlePlayerLeft(String tableId, int remainingCount, int minRequired) {
        if (remainingCount < minRequired && activeCountdowns.containsKey(tableId)) {
            ScheduledFuture<?> task = activeCountdowns.remove(tableId);
            if (task != null) {
                task.cancel(true);
            }
            log.info("Player left table [{}]. Seated count {} below required {}. Countdown cancelled.", tableId, remainingCount, minRequired);
            
            Optional<Table> opt = tableRepository.findById(tableId);
            if (opt.isPresent()) {
                Table t = opt.get();
                t.setStatus(TableStatus.WAITING);
                tableRepository.save(t);
                eventPublisher.publishTableUpdated(tableId, t);
            }
            eventPublisher.publishCountdownCancelled(tableId, "Player left table");
        }
    }

    public synchronized void startNewRound(String tableId) {
        cancelTurnTimer(tableId);

        Optional<Table> opt = tableRepository.findById(tableId);
        if (opt.isEmpty()) return;
        Table table = opt.get();

        List<String> seated = table.getSeatedPlayerIds();
        if (seated == null || seated.size() < 2) {
            table.setStatus(TableStatus.WAITING);
            tableRepository.save(table);
            eventPublisher.publishTableUpdated(tableId, table);
            return;
        }

        table.setStatus(TableStatus.DEALING);
        table.setRoundNumber(table.getRoundNumber() + 1);

        // Rotate dealer seat
        int currentDealer = dealerSeats.getOrDefault(tableId, 0);
        int nextDealer = (currentDealer + 1) % seated.size();
        dealerSeats.put(tableId, nextDealer);
        table.setDealerSeatIndex(nextDealer);

        // Deduct boot amounts (e.g., 1000 paise = ₹10)
        long bootPaise = 1000L;
        long totalPot = 0L;
        for (String playerId : seated) {
            try {
                String refId = "boot:" + tableId + ":" + System.currentTimeMillis() + ":" + playerId;
                walletService.applyLedgerEntry(playerId, LedgerEntryType.BET, bootPaise, refId);
                totalPot += bootPaise;
            } catch (Exception ex) {
                log.warn("Could not deduct boot amount from user [{}]", playerId, ex);
            }
        }

        table.setPotPaise(totalPot);
        table.setActivePlayerIds(new ArrayList<>(seated));
        table.setBlindPlayerIds(new ArrayList<>(seated));
        table.setSeenPlayerIds(new ArrayList<>());
        table.setPackedPlayerIds(new ArrayList<>());
        table.setWinnerUserId(null);

        // Initialize engine and shuffle deck
        GameEngineConfig config = GameEngineConfig.defaultConfig(bootPaise, 50000L);
        BettingRoundEngine engine = new BettingRoundEngine(config);
        Deck deck = new Deck();
        deck.shuffle();

        engine.startHand(seated, deck);
        activeEngines.put(tableId, engine);

        table.setStatus(TableStatus.PLAYING);
        tableRepository.save(table);

        eventPublisher.publishGameStarted(tableId, Map.of(
                "tableId", tableId,
                "seatedPlayers", seated,
                "bootPaise", bootPaise,
                "potPaise", totalPot,
                "dealerSeatIndex", nextDealer
        ));

        eventPublisher.publishDealerSelected(tableId, nextDealer);
        eventPublisher.publishPotUpdated(tableId, totalPot);
        eventPublisher.publishTableUpdated(tableId, table);

        // Notify cards distributed
        eventPublisher.publishCardsDistributed(tableId, Map.of("message", "Cards dealt privately to all players."));

        // Start turn for seat after dealer
        int firstTurnIndex = (nextDealer + 1) % seated.size();
        startTurn(tableId, seated.get(firstTurnIndex), firstTurnIndex);
    }

    private void startTurn(String tableId, String userId, int seatIndex) {
        cancelTurnTimer(tableId);

        Optional<Table> opt = tableRepository.findById(tableId);
        if (opt.isPresent()) {
            Table t = opt.get();
            t.setCurrentTurnUserId(userId);
            tableRepository.save(t);
            eventPublisher.publishTableUpdated(tableId, t);
        }

        log.info("Turn started for user [{}] at seat [{}] on table [{}]", userId, seatIndex, tableId);
        eventPublisher.publishTurnStarted(tableId, userId, seatIndex, TURN_TIMEOUT_SECONDS);

        // Schedule 20s turn timeout -> auto-pack
        ScheduledFuture<?> timerTask = scheduler.schedule(() -> {
            log.warn("Turn timeout expired for user [{}] on table [{}]. Auto-packing player.", userId, tableId);
            processAutoPack(tableId, userId);
        }, TURN_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        activeTurnTimers.put(tableId, timerTask);
    }

    private void cancelTurnTimer(String tableId) {
        ScheduledFuture<?> timer = activeTurnTimers.remove(tableId);
        if (timer != null) {
            timer.cancel(true);
        }
    }

    public synchronized void processAction(String tableId, String userId, String actionType, long betAmount) {
        BettingRoundEngine engine = activeEngines.get(tableId);
        if (engine == null) {
            log.warn("No active game engine for table [{}]", tableId);
            return;
        }

        cancelTurnTimer(tableId);

        try {
            Optional<Table> optTable = tableRepository.findById(tableId);
            Table table = optTable.orElse(null);

            if ("SEE_CARDS".equalsIgnoreCase(actionType)) {
                engine.applyAction(PlayerAction.of(userId, PlayerActionType.SEE_CARDS));
                if (table != null) {
                    table.getBlindPlayerIds().remove(userId);
                    if (!table.getSeenPlayerIds().contains(userId)) table.getSeenPlayerIds().add(userId);
                    table.setLastAction(userId + " saw cards");
                    tableRepository.save(table);
                    eventPublisher.publishTableUpdated(tableId, table);
                }
                eventPublisher.publishSeenPlayed(tableId, userId);
                return;
            } else if ("CHAAL".equalsIgnoreCase(actionType) || "PLAY_BLIND".equalsIgnoreCase(actionType)) {
                long required = engine.getRequiredBetPaise(userId);
                long bet = Math.max(betAmount, required);
                String refId = "bet:" + tableId + ":" + System.currentTimeMillis() + ":" + userId;
                walletService.applyLedgerEntry(userId, LedgerEntryType.BET, bet, refId);

                engine.applyAction(PlayerAction.of(userId, PlayerActionType.CHAAL, bet));
                if (table != null) {
                    table.setPotPaise(engine.getPotPaise());
                    table.setLastAction(userId + " played Chaal ₹" + (bet / 100));
                    tableRepository.save(table);
                    eventPublisher.publishTableUpdated(tableId, table);
                }
                eventPublisher.publishBlindPlayed(tableId, userId, bet, engine.getPotPaise());
            } else if ("RAISE".equalsIgnoreCase(actionType)) {
                long bet = betAmount > 0 ? betAmount : (engine.getRequiredBetPaise(userId) * 2);
                String refId = "bet:" + tableId + ":" + System.currentTimeMillis() + ":" + userId;
                walletService.applyLedgerEntry(userId, LedgerEntryType.BET, bet, refId);

                engine.applyAction(PlayerAction.of(userId, PlayerActionType.RAISE, bet));
                if (table != null) {
                    table.setPotPaise(engine.getPotPaise());
                    table.setLastAction(userId + " raised ₹" + (bet / 100));
                    tableRepository.save(table);
                    eventPublisher.publishTableUpdated(tableId, table);
                }
                eventPublisher.publishRaisePlayed(tableId, userId, bet, engine.getPotPaise());
            } else if ("PACK".equalsIgnoreCase(actionType)) {
                engine.applyAction(PlayerAction.of(userId, PlayerActionType.PACK));
                if (table != null) {
                    table.getActivePlayerIds().remove(userId);
                    if (!table.getPackedPlayerIds().contains(userId)) table.getPackedPlayerIds().add(userId);
                    table.setLastAction(userId + " packed");
                    tableRepository.save(table);
                    eventPublisher.publishTableUpdated(tableId, table);
                }
                eventPublisher.publishPackPlayed(tableId, userId, false);
            } else if ("SIDE_SHOW_REQUEST".equalsIgnoreCase(actionType)) {
                eventPublisher.publishSideShowRequested(tableId, userId, "target");
                return;
            } else if ("SHOW".equalsIgnoreCase(actionType)) {
                long required = engine.getRequiredBetPaise(userId);
                String refId = "bet:" + tableId + ":" + System.currentTimeMillis() + ":" + userId;
                walletService.applyLedgerEntry(userId, LedgerEntryType.BET, required, refId);

                engine.applyAction(PlayerAction.of(userId, PlayerActionType.SHOW, required));
                if (table != null) {
                    table.setStatus(TableStatus.SHOW);
                    table.setLastAction(userId + " requested Show");
                    tableRepository.save(table);
                    eventPublisher.publishTableUpdated(tableId, table);
                }
                eventPublisher.publishShowRequested(tableId, Map.of("requesterId", userId));
            }

            eventPublisher.publishPotUpdated(tableId, engine.getPotPaise());

            if (engine.isHandFinished()) {
                handleHandFinished(tableId, engine);
            } else {
                String nextUser = engine.getCurrentTurnPlayerId();
                if (table != null && nextUser != null) {
                    int nextSeat = table.getSeatedPlayerIds().indexOf(nextUser);
                    startTurn(tableId, nextUser, nextSeat);
                }
            }
        } catch (Exception e) {
            log.error("Error applying action [{}] for user [{}] on table [{}]: {}", actionType, userId, tableId, e.getMessage());
        }
    }

    public synchronized void processAutoPack(String tableId, String userId) {
        BettingRoundEngine engine = activeEngines.get(tableId);
        if (engine == null || engine.isHandFinished()) return;

        try {
            engine.applyAction(PlayerAction.of(userId, PlayerActionType.PACK));

            Optional<Table> optTable = tableRepository.findById(tableId);
            if (optTable.isPresent()) {
                Table table = optTable.get();
                table.getActivePlayerIds().remove(userId);
                if (!table.getPackedPlayerIds().contains(userId)) table.getPackedPlayerIds().add(userId);
                table.setLastAction(userId + " auto-packed (timeout)");
                tableRepository.save(table);
                eventPublisher.publishTableUpdated(tableId, table);
            }

            eventPublisher.publishPackPlayed(tableId, userId, true);

            if (engine.isHandFinished()) {
                handleHandFinished(tableId, engine);
            } else {
                String nextUser = engine.getCurrentTurnPlayerId();
                if (optTable.isPresent() && nextUser != null) {
                    int nextSeat = optTable.get().getSeatedPlayerIds().indexOf(nextUser);
                    startTurn(tableId, nextUser, nextSeat);
                }
            }
        } catch (Exception e) {
            log.error("Error auto-packing user [{}] on table [{}]: {}", userId, tableId, e.getMessage());
        }
    }

    private void handleHandFinished(String tableId, BettingRoundEngine engine) {
        cancelTurnTimer(tableId);
        HandOutcome outcome = engine.getOutcome();
        if (outcome == null) return;

        String winnerId = outcome.getWinnerId();
        long payoutPaise = outcome.getWinnerPayoutPaise();

        log.info("Hand finished on table [{}]. Winner [{}] wins payout {} paise", tableId, winnerId, payoutPaise);

        // Save Table state
        Optional<Table> optTable = tableRepository.findById(tableId);
        if (optTable.isPresent()) {
            Table table = optTable.get();
            table.setStatus(TableStatus.ROUND_END);
            table.setWinnerUserId(winnerId);
            table.setLastAction("Winner: " + winnerId);
            tableRepository.save(table);
            eventPublisher.publishTableUpdated(tableId, table);
        }

        // Credit pot to winner wallet
        try {
            String refId = "win:" + tableId + ":" + System.currentTimeMillis() + ":" + winnerId;
            walletService.applyLedgerEntry(winnerId, LedgerEntryType.WIN, payoutPaise, refId);
        } catch (Exception ex) {
            log.error("Error crediting winnings to winner [{}]", winnerId, ex);
        }

        // Increment matchesPlayedCount for seated players
        if (optTable.isPresent()) {
            for (String pid : optTable.get().getSeatedPlayerIds()) {
                userRepository.findById(pid).ifPresent(u -> {
                    u.setMatchesPlayedCount(u.getMatchesPlayedCount() + 1);
                    userRepository.save(u);
                });
            }
        }

        // Save MatchHistory audit document
        try {
            MatchHistory history = MatchHistory.builder()
                    .tableId(tableId)
                    .winnerId(winnerId)
                    .potAmountPaise(outcome.getPotAmountPaise())
                    .rakeAmountPaise(outcome.getRakeAmountPaise())
                    .handSummary(HandSummary.builder()
                            .winningHandName(outcome.getWinningCategory() != null ? outcome.getWinningCategory().name() : "FOLD_WIN")
                            .notes(outcome.getNotes() != null ? outcome.getNotes() : "Hand completed")
                            .build())
                    .startedAt(Instant.now())
                    .endedAt(Instant.now())
                    .build();
            matchHistoryRepository.save(history);
        } catch (Exception ex) {
            log.warn("Could not save MatchHistory for table [{}]", tableId, ex);
        }

        eventPublisher.publishWinnerDeclared(tableId, Map.of(
                "winnerUserId", winnerId,
                "potPaise", outcome.getPotAmountPaise(),
                "payoutPaise", payoutPaise,
                "winningCategory", outcome.getWinningCategory() != null ? outcome.getWinningCategory().name() : "FOLD_WIN",
                "notes", outcome.getNotes() != null ? outcome.getNotes() : "Hand completed"
        ));

        eventPublisher.publishRoundFinished(tableId, NEXT_ROUND_DELAY_SECONDS);

        // Schedule next round auto-start after 5 seconds
        scheduler.schedule(() -> {
            startNewRound(tableId);
        }, NEXT_ROUND_DELAY_SECONDS, TimeUnit.SECONDS);
    }
}
