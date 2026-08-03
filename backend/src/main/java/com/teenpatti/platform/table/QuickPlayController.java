package com.teenpatti.platform.table;

import com.teenpatti.platform.common.response.ApiResponse;
import com.teenpatti.platform.matchmaking.MatchmakingService;
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

/**
 * Quick Play: smart matchmaking with optional AI bot fill.
 */
@Slf4j
@RestController
@RequestMapping("/api/tables")
@RequiredArgsConstructor
public class QuickPlayController {

    private final MatchmakingService matchmakingService;

    @PostMapping("/quick-play")
    public ResponseEntity<ApiResponse<JoinTableResponse>> quickPlay(
            Authentication authentication,
            @Valid @RequestBody QuickPlayRequest request) {

        String userId = authentication.getPrincipal().toString();
        long targetBootPaise = request.getBootAmountPaise() != null ? request.getBootAmountPaise() : 1000L;
        GameVariant selectedVariant = GameVariantResolver.resolve(request.getGameVariant());

        log.info("Quick Play request for user [{}] boot {} paise variant {}", userId, targetBootPaise, selectedVariant);

        JoinTableResponse joinResponse = matchmakingService.quickPlay(userId, targetBootPaise, selectedVariant);
        return ResponseEntity.ok(ApiResponse.success("Matchmaking seat reserved", joinResponse));
    }
}
