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

    public PageResponse<TableSummaryResponse> getPublicTables(StakeTier stakeTier, int page, int size) {
        int boundedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int boundedPage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(boundedPage, boundedSize);

        Page<Table> tablePage;
        if (stakeTier != null) {
            tablePage = tableRepository.findAvailablePublicTablesByStakeTier(stakeTier, pageable);
        } else {
            tablePage = tableRepository.findAvailablePublicTables(pageable);
        }

        return PageResponse.from(tablePage, this::toTableSummaryResponse);
    }

    public PrivateTableCreatedResponse createPrivateTable(String userId, CreatePrivateTableRequest request) {
        String inviteCode = inviteCodeGenerator.generateUniqueInviteCode();

        List<String> initialSeated = new ArrayList<>();
        initialSeated.add(userId);

        Table table = Table.builder()
                .tableType(TableType.PRIVATE)
                .stakeTier(request.getStakeTier())
                .maxPlayers(request.getMaxPlayers())
                .inviteCode(inviteCode)
                .seatedPlayerIds(initialSeated)
                .status(TableStatus.WAITING)
                .createdAt(Instant.now())
                .build();

        Table savedTable = tableRepository.save(table);
        log.info("Created PRIVATE table [{}] with inviteCode [{}] by user [{}]",
                savedTable.getId(), inviteCode, userId);

        return PrivateTableCreatedResponse.builder()
                .tableId(savedTable.getId())
                .inviteCode(inviteCode)
                .build();
    }

    public TableSummaryResponse getPrivateTableByInviteCode(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new TableNotFoundException("Private table not found or invalid invite code.");
        }

        Table table = tableRepository.findByInviteCode(inviteCode.trim())
                .orElseThrow(() -> new TableNotFoundException("Private table not found or invalid invite code."));

        if (table.getTableType() != TableType.PRIVATE || table.getStatus() == TableStatus.CLOSED) {
            throw new TableNotFoundException("Private table not found or invalid invite code.");
        }

        return toTableSummaryResponse(table);
    }

    public EligibilityCheckResponse checkJoinEligibility(String userId, String tableId) {
        Table table = tableRepository.findById(tableId)
                .orElseThrow(() -> new TableNotFoundException("Table not found: " + tableId));

        if (table.getStatus() == TableStatus.CLOSED) {
            return EligibilityCheckResponse.ineligible("TABLE_CLOSED", null, null);
        }

        int currentPlayers = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds().size() : 0;
        if (currentPlayers >= table.getMaxPlayers()) {
            return EligibilityCheckResponse.ineligible("TABLE_FULL", null, null);
        }

        long minRequiredPaise = stakeTierConfig.getMinBuyInPaise(table.getStakeTier());
        WalletBalanceResponse balanceResponse = walletService.getBalance(userId);
        long currentBalancePaise = balanceResponse.getBalancePaise();

        if (currentBalancePaise < minRequiredPaise) {
            log.info("Eligibility check failed for user [{}] on table [{}]: required {} paise, available {} paise",
                    userId, tableId, minRequiredPaise, currentBalancePaise);
            return EligibilityCheckResponse.ineligible("INSUFFICIENT_BALANCE", minRequiredPaise, currentBalancePaise);
        }

        return EligibilityCheckResponse.eligible(minRequiredPaise, currentBalancePaise);
    }

    private TableSummaryResponse toTableSummaryResponse(Table table) {
        int playerCount = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds().size() : 0;
        return TableSummaryResponse.builder()
                .tableId(table.getId())
                .stakeTier(table.getStakeTier())
                .maxPlayers(table.getMaxPlayers())
                .currentPlayerCount(playerCount)
                .status(table.getStatus())
                .tableType(table.getTableType())
                .build();
    }
}
