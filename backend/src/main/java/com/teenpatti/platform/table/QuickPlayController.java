package com.teenpatti.platform.table;

import com.teenpatti.platform.common.response.ApiResponse;
import com.teenpatti.platform.lobby.LobbyService;
import com.teenpatti.platform.lobby.dto.CreatePrivateTableRequest;
import com.teenpatti.platform.lobby.dto.TableSummaryResponse;
import com.teenpatti.platform.table.dto.JoinTableResponse;
import com.teenpatti.platform.table.dto.QuickPlayRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Quick Play: join an open public table or auto-create one at the requested boot amount.
 */
@Slf4j
@RestController
@RequestMapping("/api/tables")
@RequiredArgsConstructor
public class QuickPlayController {

    private final TableService tableService;
    private final LobbyService lobbyService;
    private final PublicTableService publicTableService;

    @PostMapping("/quick-play")
    public ResponseEntity<ApiResponse<JoinTableResponse>> quickPlay(
            Authentication authentication,
            @Valid @RequestBody QuickPlayRequest request) {

        String userId = authentication.getPrincipal().toString();
        long targetBootPaise = request.getBootAmountPaise() != null ? request.getBootAmountPaise() : 1000L;
        GameVariant selectedVariant = GameVariantResolver.resolve(request.getGameVariant());

        log.info("Quick Play request for user [{}] boot {} paise variant {}", userId, targetBootPaise, selectedVariant);

        List<Table> candidates = publicTableService.findQuickPlayCandidates(targetBootPaise, userId, selectedVariant);
        if (!candidates.isEmpty()) {
            Table targetTable = candidates.get(0);
            log.info("Quick Play joining existing public table [{}] for user [{}]", targetTable.getId(), userId);
            JoinTableResponse joinResponse = tableService.joinTable(userId, targetTable.getId());
            return ResponseEntity.ok(ApiResponse.success("Quick Play successfully joined table", joinResponse));
        }

        log.info("Quick Play creating new public table for user [{}] boot {} paise", userId, targetBootPaise);

        StakeTier tier = targetBootPaise <= 1000L ? StakeTier.LOW
                : targetBootPaise <= 5000L ? StakeTier.MEDIUM
                : StakeTier.HIGH;

        CreatePrivateTableRequest createReq = CreatePrivateTableRequest.builder()
                .tableName("Teen Patti Quick Play ₹" + (targetBootPaise / 100))
                .stakeTier(tier)
                .bootAmount(targetBootPaise)
                .gameVariant(selectedVariant.name())
                .minPlayers(3)
                .maxPlayers(6)
                .build();

        TableSummaryResponse summary = lobbyService.createPublicTable(userId, createReq);

        JoinTableResponse joinResponse = JoinTableResponse.builder()
                .tableId(summary.getTableId())
                .seatIndex(0)
                .heldBuyInPaise(summary.getBootAmount())
                .tableDetail(tableService.getTableDetails(userId, summary.getTableId()))
                .build();

        return ResponseEntity.ok(ApiResponse.success("Quick Play table auto-created", joinResponse));
    }
}
