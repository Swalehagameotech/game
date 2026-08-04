package com.teenpatti.platform.bot;

import com.teenpatti.platform.bot.event.BotActionNeededEvent;
import com.teenpatti.platform.game.GameEngineService;
import com.teenpatti.platform.game.betting.BettingLogicService;
import com.teenpatti.platform.game.betting.BettingState;
import com.teenpatti.platform.game.engine.BettingRoundEngine;
import com.teenpatti.platform.game.engine.Card;
import com.teenpatti.platform.game.engine.HandContextManager;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableRepository;
import com.teenpatti.platform.table.TableService;
import com.teenpatti.platform.user.AccountStatus;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.user.UserRole;
import com.teenpatti.platform.wallet.WalletService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

/**
 * Creates bot users, seats them via the normal table API, and schedules human-like actions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BotService {

    private static final long BOT_WALLET_SEED_PAISE = 5_000_000L; // ₹50,000 play chips

    private final UserRepository userRepository;
    private final WalletService walletService;
    private final TableService tableService;
    private final TableRepository tableRepository;
    private final GameEngineService gameEngineService;
    private final BettingLogicService bettingLogicService;
    private final HandContextManager handContextManager;
    private final BotDecisionEngine decisionEngine;

    private final ConcurrentHashMap<String, BotProfile> profilesByUserId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingActions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors()),
            r -> {
                Thread t = new Thread(r, "bot-think");
                t.setDaemon(true);
                return t;
            });

    private static final String[] BOT_AVATARS = {
            "/avatars/bot-m1.png",
            "/avatars/bot-m2.png",
            "/avatars/bot-m3.png",
            "/avatars/bot-f1.png",
            "/avatars/bot-f2.png",
            "/avatars/bot-f3.png",
    };

    @PreDestroy
    void shutdown() {
        pendingActions.values().forEach(f -> f.cancel(false));
        scheduler.shutdownNow();
    }

    public boolean isBot(String userId) {
        if (userId == null) return false;
        if (profilesByUserId.containsKey(userId)) return true;
        return userRepository.findById(userId).map(User::isBot).orElse(false);
    }

    public BotProfile getProfile(String userId) {
        return profilesByUserId.get(userId);
    }

    /**
     * Seats {@code count} bots on the table through normal join validation.
     * @return userIds of bots that successfully joined
     */
    public List<String> fillSeats(String tableId, int count) {
        if (count <= 0) return List.of();
        Table table = tableRepository.findById(tableId).orElse(null);
        if (table == null) return List.of();

        List<String> joined = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            try {
                User bot = acquireOrCreateBotUser();
                ensureFunded(bot.getId(), table.getBootAmountPaise());
                tableService.joinTable(bot.getId(), tableId);
                BotProfile profile = createProfile(bot);
                profilesByUserId.put(bot.getId(), profile);
                joined.add(bot.getId());
                log.info("Bot [{}] ({}) seated on table [{}] as {}",
                        bot.getDisplayName(), bot.getId(), tableId, profile.getPersonality());
            } catch (Exception ex) {
                log.warn("Failed to seat bot on table [{}]: {}", tableId, ex.getMessage());
                break;
            }
        }
        return joined;
    }

    /**
     * Removes bots from a table after the match (or when humans leave).
     */
    public void leaveTableBots(String tableId) {
        Table table = tableRepository.findById(tableId).orElse(null);
        if (table == null || table.getSeatedPlayerIds() == null) return;

        List<String> botIds = table.getSeatedPlayerIds().stream()
                .filter(this::isBot)
                .toList();
        for (String botId : botIds) {
            cancelPending(botId);
            try {
                tableService.leaveTable(botId, tableId);
            } catch (Exception ex) {
                log.debug("Bot leave failed for [{}] on [{}]: {}", botId, tableId, ex.getMessage());
            }
            profilesByUserId.remove(botId);
        }
    }

    public void scheduleAction(String tableId, String userId) {
        scheduleAction(tableId, userId, false);
    }

    public void scheduleAction(String tableId, String userId, boolean promptOnly) {
        cancelPending(userId);
        BotProfile profile = profilesByUserId.computeIfAbsent(userId, id ->
                userRepository.findById(id).map(this::createProfile).orElse(null));
        if (profile == null) return;

        long delayMs = promptOnly ? promptDelayMs(profile) : thinkDelayMs(profile);
        ScheduledFuture<?> future = scheduler.schedule(
                () -> executeBotTurn(tableId, userId, profile),
                delayMs,
                TimeUnit.MILLISECONDS);
        pendingActions.put(userId, future);
        log.debug("Bot [{}] thinking {}ms on table [{}] (prompt={})", userId, delayMs, tableId, promptOnly);
    }

    @EventListener
    public void onBotActionNeeded(BotActionNeededEvent event) {
        if (event == null || !isBot(event.userId())) return;
        scheduleAction(event.tableId(), event.userId(), event.promptOnly());
    }

    private void executeBotTurn(String tableId, String userId, BotProfile profile) {
        try {
            Optional<BettingRoundEngine> engineOpt = handContextManager.getEngine(tableId);
            if (engineOpt.isEmpty()) return;
            BettingRoundEngine engine = engineOpt.get();
            Table table = tableRepository.findById(tableId).orElse(null);
            if (table == null) return;

            BettingState state = bettingLogicService.buildBettingState(table, engine, userId);
            if (!state.isMyTurn() && state.getAllowedActions().stream()
                    .noneMatch(a -> a.contains("ACCEPT") || a.contains("REJECT")
                            || a.equals("DISCARD_CARD") || a.startsWith("AUCTION"))) {
                return;
            }

            List<Card> cards = engine.getPlayerCards(userId);
            int roundHint = estimateRound(engine);
            BotDecision decision = decisionEngine.decide(profile, state, cards, roundHint);

            // Side-show / show responses must never "see cards first" and skip the reply
            boolean isPromptResponse = state.getAllowedActions().stream()
                    .anyMatch(a -> a.contains("ACCEPT") || a.contains("REJECT"));

            if (!isPromptResponse && decision.isSeeCardsFirst()) {
                try {
                    gameEngineService.seeCards(userId, tableId);
                    // Brief pause after seeing, then re-decide
                    Thread.sleep(400L + ThreadLocalRandom.current().nextLong(800));
                    state = bettingLogicService.buildBettingState(table, engine, userId);
                    cards = engine.getPlayerCards(userId);
                    decision = decisionEngine.decide(profile, state, cards, roundHint);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception ex) {
                    log.debug("Bot see-cards skipped: {}", ex.getMessage());
                }
            }

            String action = decision.getActionType();
            if (action == null || action.isBlank()) return;

            Integer cardIndex = null;
            if ("DISCARD_CARD".equals(action) && cards != null && !cards.isEmpty()) {
                cardIndex = ThreadLocalRandom.current().nextInt(cards.size());
            }

            String result;
            if (cardIndex != null) {
                result = gameEngineService.processAction(tableId, userId, action, decision.getAmountPaise(), cardIndex);
            } else {
                result = gameEngineService.processAction(tableId, userId, action, decision.getAmountPaise());
            }
            if (result != null) {
                log.debug("Bot action rejected [{}] {}: {}", userId, action, result);
                // Stay in pot — try call/chaal/blind before packing
                String fallback = null;
                List<String> allowed = state.getAllowedActions();
                if (allowed != null) {
                    for (String prefer : List.of("CHAAL", "CALL", "BLIND", "PLAY_BLIND", "RAISE")) {
                        if (allowed.contains(prefer)) {
                            fallback = prefer;
                            break;
                        }
                    }
                    if (fallback == null && allowed.contains("PACK")) {
                        fallback = "PACK";
                    }
                }
                if (fallback != null) {
                    long amt = 0L;
                    if ("RAISE".equals(fallback) && state.getRaiseOptionsPaise() != null
                            && !state.getRaiseOptionsPaise().isEmpty()) {
                        amt = state.getRaiseOptionsPaise().get(0);
                    }
                    gameEngineService.processAction(tableId, userId, fallback, amt);
                }
            } else {
                log.info("Bot [{}] played {} ({})", profile.getDisplayName(), action, decision.getReason());
            }
        } catch (Exception ex) {
            log.warn("Bot turn failed for [{}] on [{}]: {}", userId, tableId, ex.getMessage());
        } finally {
            pendingActions.remove(userId);
        }
    }

    private int estimateRound(BettingRoundEngine engine) {
        // Approximate: how many times pot grew beyond boots — coarse human-like "rounds seen"
        long pot = engine.getPotPaise();
        long boot = engine.getConfig() != null ? engine.getConfig().getBootAmountPaise() : 1000L;
        int active = engine.getActivePlayerIds() != null ? engine.getActivePlayerIds().size() : 3;
        if (boot <= 0) return 1;
        return Math.max(1, (int) (pot / Math.max(boot * Math.max(active, 1), 1)));
    }

    private long promptDelayMs(BotProfile profile) {
        // Side-show / show replies: 1.5–4.5s (feel human, don't stall the table)
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double base = 1500 + r.nextDouble() * 3000;
        base *= profile.getPersonality().thinkSpeed();
        return Math.max(1200L, Math.min(5000L, (long) base));
    }

    private long thinkDelayMs(BotProfile profile) {
        // 2–8 seconds, personality-scaled; occasionally nearly timeout-slow
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double base = 2000 + r.nextDouble() * 6000;
        base *= profile.getPersonality().thinkSpeed();
        if (r.nextDouble() < 0.08) {
            base = 9000 + r.nextDouble() * 4000; // dramatic stall
        }
        if (r.nextDouble() < 0.12) {
            base = 1200 + r.nextDouble() * 800; // snap decision
        }
        return Math.max(1200L, Math.min(12000L, (long) base));
    }

    private User acquireOrCreateBotUser() {
        // Prefer recycling idle bots not currently seated
        List<User> bots = userRepository.findByBotTrue();
        Set<String> seatedEverywhere = new HashSet<>();
        tableRepository.findAll().forEach(t -> {
            if (t.getSeatedPlayerIds() != null) seatedEverywhere.addAll(t.getSeatedPlayerIds());
        });
        for (User bot : bots) {
            if (!seatedEverywhere.contains(bot.getId()) && bot.getAccountStatus() == AccountStatus.ACTIVE) {
                // Upgrade legacy cartoon avatars to AI portraits
                if (bot.getAvatarUrl() == null || bot.getAvatarUrl().contains("dicebear")) {
                    bot.setAvatarUrl(randomAiAvatar());
                    return userRepository.save(bot);
                }
                return bot;
            }
        }
        return createFreshBotUser();
    }

    private User createFreshBotUser() {
        String name;
        int attempts = 0;
        do {
            name = BotNamePool.randomDisplayName();
            attempts++;
        } while (userRepository.existsByDisplayName(name) && attempts < 40);
        if (userRepository.existsByDisplayName(name)) {
            name = name + " " + ThreadLocalRandom.current().nextInt(100, 999);
        }

        String avatar = randomAiAvatar();

        User bot = User.builder()
                .displayName(name)
                .avatarUrl(avatar)
                .passwordHash("{noop}bot-disabled")
                .role(UserRole.PLAYER)
                .bot(true)
                .accountStatus(AccountStatus.ACTIVE)
                .walletBalance(0L)
                .build();
        bot = userRepository.save(bot);
        walletService.getBalance(bot.getId()); // ensure wallet row exists
        walletService.depositDemoChips(bot.getId(), BOT_WALLET_SEED_PAISE);
        log.info("Created bot user [{}] with AI avatar {}", bot.getId(), avatar);
        return bot;
    }

    private static String randomAiAvatar() {
        return BOT_AVATARS[ThreadLocalRandom.current().nextInt(BOT_AVATARS.length)];
    }

    private void ensureFunded(String userId, long bootPaise) {
        walletService.getBalance(userId);
        long balance = walletService.getBalance(userId).getBalancePaise();
        long need = Math.max(bootPaise * 50, 100_000L);
        if (balance < need) {
            walletService.depositDemoChips(userId, need);
        }
    }

    private BotProfile createProfile(User bot) {
        BotPersonality personality = BotPersonality.randomStrong();
        int seeAfter = switch (personality) {
            case AGGRESSIVE, RISKY, BLUFFER -> ThreadLocalRandom.current().nextInt(3, 6);
            case DEFENSIVE, PROFESSIONAL -> ThreadLocalRandom.current().nextInt(2, 4);
            default -> ThreadLocalRandom.current().nextInt(2, 5);
        };
        return BotProfile.builder()
                .userId(bot.getId())
                .displayName(bot.getDisplayName())
                .avatarUrl(bot.getAvatarUrl())
                .personality(personality)
                .preferSeeAfterRounds(seeAfter)
                .build();
    }

    private void cancelPending(String userId) {
        ScheduledFuture<?> f = pendingActions.remove(userId);
        if (f != null) f.cancel(false);
    }
}
