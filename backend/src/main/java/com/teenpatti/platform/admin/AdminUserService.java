package com.teenpatti.platform.admin;

import com.teenpatti.platform.auth.RefreshTokenRepository;
import com.teenpatti.platform.common.exception.ResourceNotFoundException;
import com.teenpatti.platform.user.AccountStatus;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

import com.teenpatti.platform.notification.NotificationService;
import com.teenpatti.platform.notification.NotificationType;

/**
 * Service managing administrative account state transitions (SUSPEND, BAN, REINSTATE)
 * and active session token invalidations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AdminActionLogService adminActionLogService;
    private final NotificationService notificationService;

    /**
     * Temporarily suspends a user's account and revokes all active refresh tokens.
     */
    public User suspendUser(String adminUserId, String targetUserId, String reason) {
        User user = getUserOrThrow(targetUserId);

        user.setAccountStatus(AccountStatus.SUSPENDED);
        User updated = userRepository.save(user);

        // Immediately revoke active refresh tokens to force logout
        refreshTokenRepository.deleteByUserId(targetUserId);

        adminActionLogService.logAction(
                adminUserId,
                AdminActionType.ACCOUNT_SUSPEND,
                targetUserId,
                Map.of("targetUserId", targetUserId, "reason", reason)
        );

        log.info("Admin [{}] SUSPENDED user [{}] with reason: {}", adminUserId, targetUserId, reason);
        // Phase 14: Retroactive notification call
        notificationService.notify(
                targetUserId,
                NotificationType.ACCOUNT_ALERT,
                "Your account has been suspended. Reason: " + reason
        );
        return updated;
    }

    /**
     * Permanently bans a user's account and revokes all active refresh tokens.
     */
    public User banUser(String adminUserId, String targetUserId, String reason) {
        User user = getUserOrThrow(targetUserId);

        user.setAccountStatus(AccountStatus.BANNED);
        User updated = userRepository.save(user);

        // Immediately revoke active refresh tokens
        refreshTokenRepository.deleteByUserId(targetUserId);

        adminActionLogService.logAction(
                adminUserId,
                AdminActionType.ACCOUNT_BAN,
                targetUserId,
                Map.of("targetUserId", targetUserId, "reason", reason)
        );

        log.info("Admin [{}] BANNED user [{}] with reason: {}", adminUserId, targetUserId, reason);
        return updated;
    }

    /**
     * Reinstates a SUSPENDED user back to ACTIVE status.
     */
    public User reinstateUser(String adminUserId, String targetUserId) {
        User user = getUserOrThrow(targetUserId);

        if (user.getAccountStatus() != AccountStatus.SUSPENDED) {
            throw new IllegalStateException("Only SUSPENDED users can be reinstated. Current status: " + user.getAccountStatus());
        }

        user.setAccountStatus(AccountStatus.ACTIVE);
        User updated = userRepository.save(user);

        adminActionLogService.logAction(
                adminUserId,
                AdminActionType.ACCOUNT_REINSTATE,
                targetUserId,
                Map.of("targetUserId", targetUserId)
        );

        log.info("Admin [{}] REINSTATED user [{}] to ACTIVE", adminUserId, targetUserId);
        return updated;
    }

    private User getUserOrThrow(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }
}
