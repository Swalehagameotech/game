package com.teenpatti.platform.admin;

import com.teenpatti.platform.common.exception.ResourceNotFoundException;
import com.teenpatti.platform.game.MatchHistory;
import com.teenpatti.platform.game.MatchHistoryRepository;
import com.teenpatti.platform.transaction.LedgerEntry;
import com.teenpatti.platform.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/investigate")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminInvestigationController {

    private final WalletService walletService;
    private final MatchHistoryRepository matchHistoryRepository;

    @GetMapping("/user/{userId}/ledger")
    public ResponseEntity<Page<LedgerEntry>> getUserLedgerHistory(
            @PathVariable String userId,
            Pageable pageable) {
        return ResponseEntity.ok(walletService.getLedgerEntries(userId, pageable));
    }

    @GetMapping("/table/{tableId}/matches")
    public ResponseEntity<Page<MatchHistory>> getTableMatchHistory(
            @PathVariable String tableId,
            Pageable pageable) {
        return ResponseEntity.ok(matchHistoryRepository.findByTableIdOrderByEndedAtDesc(tableId, pageable));
    }

    @GetMapping("/match/{matchId}")
    public ResponseEntity<MatchHistory> getMatchDetails(@PathVariable String matchId) {
        MatchHistory match = matchHistoryRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("MatchHistory not found with id: " + matchId));
        return ResponseEntity.ok(match);
    }
}
