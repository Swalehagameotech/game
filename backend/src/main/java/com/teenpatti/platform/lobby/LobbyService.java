package com.teenpatti.platform.lobby;

import com.teenpatti.platform.common.exception.TableNotFoundException;
import com.teenpatti.platform.common.response.PageResponse;
import com.teenpatti.platform.lobby.config.StakeTierConfig;
import com.teenpatti.platform.lobby.dto.CreatePrivateTableRequest;
import com.teenpatti.platform.lobby.dto.EligibilityCheckResponse;
import com.teenpatti.platform.lobby.dto.PrivateTableCreatedResponse;
import com.teenpatti.platform.lobby.dto.TableSummaryResponse;
import com.teenpatti.platform.table.StakeTier;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableRepository;
import com.teenpatti.platform.table.TableStatus;
import com.teenpatti.platform.table.TableType;
import com.teenpatti.platform.table.PublicTableService;
import com.teenpatti.platform.table.PrivateTableService;
import com.teenpatti.platform.table.dto.JoinTableResponse;
import com.teenpatti.platform.wallet.WalletService;
import com.teenpatti.platform.wallet.dto.WalletBalanceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LobbyService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TableRepository tableRepository;
    private final WalletService walletService;
    private final StakeTierConfig stakeTierConfig;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final com.teenpatti.platform.table.DefaultTableInitializer defaultTableInitializer;
    private final com.teenpatti.platform.websocket.WebSocketEventPublisher webSocketEventPublisher;
    private final PublicTableService publicTableService;
    private final PrivateTableService privateTableService;
    private final com.teenpatti.platform.table.TableService tableService;

    public List<TableSummaryResponse> getPublicTables() {
        return getPublicTables(null, 0, 50).getContent();
    }

    public PageResponse<TableSummaryResponse> getPublicTables(StakeTier stakeTier, int page, int size) {
        int boundedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int boundedPage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(boundedPage, boundedSize);

        List<Table> allPublic = tableRepository.findAll().stream()
                .filter(t -> t.getTableType() == TableType.PUBLIC)
                .filter(t -> t.getStatus() != TableStatus.CLOSED)
                .filter(t -> stakeTier == null || t.getStakeTier() == stakeTier)
                .sorted((a, b) -> {
                    int countA = a.getSeatedPlayerIds() != null ? a.getSeatedPlayerIds().size() : 0;
                    int countB = b.getSeatedPlayerIds() != null ? b.getSeatedPlayerIds().size() : 0;
                    return Integer.compare(countB, countA);
                })
                .toList();

        List<TableSummaryResponse> summaries = allPublic.stream()
                .map(this::toTableSummaryResponse)
                .toList();

        return PageResponse.<TableSummaryResponse>builder()
                .content(summaries)
                .pageNumber(boundedPage)
                .pageSize(boundedSize)
                .totalElements(summaries.size())
                .totalPages(1)
                .last(true)
                .build();
    }

    public PrivateTableCreatedResponse createPrivateTable(String userId, CreatePrivateTableRequest request) {
        String inviteCode = inviteCodeGenerator.generateUniqueInviteCode();

        int maxPlayers = request.getMaxPlayers() > 0 ? request.getMaxPlayers() : 6;
        int minPlayers = request.getMinPlayers() > 0 ? request.getMinPlayers() : 3;
        if (minPlayers > maxPlayers) {
            minPlayers = maxPlayers;
        }

        List<String> initialSeated = new ArrayList<>();
        initialSeated.add(userId);

        long bootPaise = request.getBootAmount() != null && request.getBootAmount() > 0 
                ? request.getBootAmount() 
                : stakeTierConfig.getMinBuyInPaise(request.getStakeTier());

        WalletBalanceResponse hostBalance = walletService.getBalance(userId);
        if (hostBalance.getBalancePaise() < bootPaise) {
            throw new com.teenpatti.platform.common.exception.InsufficientBalanceException(
                    "Insufficient wallet balance for boot amount. Required: " + bootPaise + " paise.");
        }

        String name = request.getTableName() != null && !request.getTableName().isBlank() 
                ? request.getTableName() 
                : "Teen Patti Private #" + inviteCode;

        com.teenpatti.platform.table.GameVariant variant =
                com.teenpatti.platform.table.GameVariantResolver.resolve(request.getGameVariant());

        Table table = Table.builder()
                .tableName(name)
                .hostId(userId)
                .tableType(TableType.PRIVATE)
                .visibility("PRIVATE")
                .stakeTier(request.getStakeTier())
                .gameVariant(variant)
                .bootAmountPaise(bootPaise)
                .minPlayers(minPlayers)
                .maxPlayers(maxPlayers)
                .inviteCode(inviteCode)
                .seatedPlayerIds(initialSeated)
                .seatMap(com.teenpatti.platform.table.TableSeatHelper.buildSeatMap(initialSeated))
                .status(TableStatus.WAITING)
                .createdAt(Instant.now())
                .build();

        Table savedTable = tableRepository.save(table);
        log.info("Created PRIVATE table [{}] with inviteCode [{}] by host [{}] (host seated as player 1)",
                savedTable.getId(), inviteCode, userId);

        privateTableService.sendInviteNotifications(savedTable, userId, request.getInviteUserIds());

        TableSummaryResponse summary = toTableSummaryResponse(savedTable);
        webSocketEventPublisher.publishTableCreated(summary);
        webSocketEventPublisher.publishPlayerJoined(savedTable.getId(), userId, 1);

        return PrivateTableCreatedResponse.builder()
                .tableId(savedTable.getId())
                .inviteCode(inviteCode)
                .build();
    }

    public TableSummaryResponse createPublicTable(String userId, CreatePrivateTableRequest request) {
        int maxPlayers = request.getMaxPlayers() > 0 ? request.getMaxPlayers() : 6;
        int minPlayers = request.getMinPlayers() > 0 ? request.getMinPlayers() : 3;
        if (minPlayers > maxPlayers) {
            minPlayers = maxPlayers;
        }

        List<String> initialSeated = new ArrayList<>();
        initialSeated.add(userId);

        long bootPaise = request.getBootAmount() != null && request.getBootAmount() > 0 
                ? request.getBootAmount() 
                : stakeTierConfig.getMinBuyInPaise(request.getStakeTier());

        WalletBalanceResponse hostBalance = walletService.getBalance(userId);
        if (hostBalance.getBalancePaise() < bootPaise) {
            throw new com.teenpatti.platform.common.exception.InsufficientBalanceException(
                    "Insufficient wallet balance for boot amount. Required: " + bootPaise + " paise.");
        }

        String name = request.getTableName() != null && !request.getTableName().isBlank() 
                ? request.getTableName() 
                : "Teen Patti Public Table";

        com.teenpatti.platform.table.GameVariant variant =
                com.teenpatti.platform.table.GameVariantResolver.resolve(request.getGameVariant());

        Table table = Table.builder()
                .tableName(name)
                .hostId(userId)
                .tableType(TableType.PUBLIC)
                .visibility("PUBLIC")
                .stakeTier(request.getStakeTier())
                .gameVariant(variant)
                .bootAmountPaise(bootPaise)
                .minPlayers(minPlayers)
                .maxPlayers(maxPlayers)
                .seatedPlayerIds(initialSeated)
                .seatMap(com.teenpatti.platform.table.TableSeatHelper.buildSeatMap(initialSeated))
                .status(TableStatus.WAITING)
                .createdAt(Instant.now())
                .build();

        Table savedTable = tableRepository.save(table);
        log.info("Created PUBLIC table [{}] by host [{}] (host seated as player 1)", savedTable.getId(), userId);

        TableSummaryResponse summary = toTableSummaryResponse(savedTable);
        webSocketEventPublisher.publishTableCreated(summary);
        webSocketEventPublisher.publishPlayerJoined(savedTable.getId(), userId, 1);
        publicTableService.afterPublicTableMutation(savedTable.getId());

        return summary;
    }

    public TableSummaryResponse getPrivateTableByInviteCode(String inviteCode) {
        Table table = privateTableService.resolvePrivateTable(inviteCode);
        return toTableSummaryResponse(table);
    }

    /**
     * Join a private table using its invite code — validates code, seats player, returns join payload.
     */
    public JoinTableResponse joinPrivateTableByInviteCode(String userId, String inviteCode) {
        Table table = privateTableService.resolvePrivateTable(inviteCode);
        privateTableService.assertJoinable(table);

        JoinTableResponse response = tableService.joinTable(userId, table.getId());
        privateTableService.markInviteNotificationsRead(userId, table.getId(), table.getInviteCode());
        return response;
    }

    public EligibilityCheckResponse checkJoinEligibility(String userId, String tableId) {
        Table table = tableRepository.findById(tableId)
                .orElseThrow(() -> new TableNotFoundException("Table not found: " + tableId));

        if (table.getStatus() == TableStatus.CLOSED) {
            return EligibilityCheckResponse.ineligible("TABLE_CLOSED", null, null);
        }

        if (table.getTableType() == TableType.PRIVATE
                && com.teenpatti.platform.table.TableStatusGroups.isRunning(table.getStatus())) {
            return EligibilityCheckResponse.ineligible("GAME_IN_PROGRESS", null, null);
        }

        int currentPlayers = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds().size() : 0;
        if (currentPlayers >= table.getMaxPlayers()) {
            return EligibilityCheckResponse.ineligible("TABLE_FULL", null, null);
        }

        long minRequiredPaise = table.getBootAmountPaise() > 0 ? table.getBootAmountPaise() : stakeTierConfig.getMinBuyInPaise(table.getStakeTier());
        WalletBalanceResponse balanceResponse = walletService.getBalance(userId);
        long currentBalancePaise = balanceResponse.getBalancePaise();

        if (currentBalancePaise < minRequiredPaise) {
            log.info("Eligibility check failed for user [{}] on table [{}]: required {} paise, available {} paise",
                    userId, tableId, minRequiredPaise, currentBalancePaise);
            return EligibilityCheckResponse.ineligible("INSUFFICIENT_BALANCE", minRequiredPaise, currentBalancePaise);
        }

        return EligibilityCheckResponse.eligible(minRequiredPaise, currentBalancePaise);
    }

    public TableSummaryResponse toTableSummary(Table table, String hostDisplayName) {
        TableSummaryResponse summary = toTableSummaryResponse(table);
        summary.setHostDisplayName(hostDisplayName != null ? hostDisplayName : "Host");
        return summary;
    }

    private TableSummaryResponse toTableSummaryResponse(Table table) {
        int playerCount = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds().size() : 0;
        long bootAmount = table.getBootAmountPaise() > 0 ? table.getBootAmountPaise() : stakeTierConfig.getMinBuyInPaise(table.getStakeTier());
        String name = table.getTableName() != null && !table.getTableName().isBlank() 
                ? table.getTableName() 
                : "Teen Patti " + (table.getTableType() != null ? table.getTableType().name() : "PUBLIC");
        String variant = table.getGameVariant() != null ? table.getGameVariant().name() : "CLASSIC";

        return TableSummaryResponse.builder()
                .tableId(table.getId())
                .tableName(name)
                .hostId(table.getHostId())
                .stakeTier(table.getStakeTier())
                .maxPlayers(table.getMaxPlayers())
                .minPlayers(table.getMinPlayers() > 0 ? table.getMinPlayers() : 3)
                .currentPlayerCount(playerCount)
                .bootAmount(bootAmount)
                .gameVariant(variant)
                .visibility(table.getVisibility() != null ? table.getVisibility() : (table.getTableType() != null ? table.getTableType().name() : "PUBLIC"))
                .status(table.getStatus())
                .tableType(table.getTableType())
                .countdownSeconds(table.getCountdownSeconds())
                .build();
    }
}
