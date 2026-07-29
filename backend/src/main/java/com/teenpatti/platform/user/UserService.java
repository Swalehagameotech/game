package com.teenpatti.platform.user;

import com.teenpatti.platform.common.exception.BadCredentialsException;
import com.teenpatti.platform.common.exception.DuplicateUserException;
import com.teenpatti.platform.common.exception.UserNotFoundException;
import com.teenpatti.platform.common.exception.AccountStatusException;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.table.TableRepository;
import com.teenpatti.platform.table.TableStatusGroups;
import com.teenpatti.platform.user.dto.ChangePasswordRequest;
import com.teenpatti.platform.user.dto.OnlinePlayersResponse;
import com.teenpatti.platform.user.dto.PublicProfileResponse;
import com.teenpatti.platform.user.dto.UpdateProfileRequest;
import com.teenpatti.platform.user.dto.UserActiveTableDto;
import com.teenpatti.platform.user.dto.UserProfileResponse;
import com.teenpatti.platform.wallet.WalletService;
import com.teenpatti.platform.wallet.dto.WalletBalanceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * User profile, presence, and account self-service (KYC deferred to a later module).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final WalletService walletService;
    private final TableRepository tableRepository;
    private final UserPresenceService userPresenceService;
    private final PasswordEncoder passwordEncoder;

    public UserProfileResponse getUserProfile(String userId) {
        User user = findActiveUser(userId);
        WalletBalanceResponse wallet = walletService.getBalance(userId);
        UserActiveTableDto activeTable = findActiveTable(userId);
        return UserMapper.toUserProfileResponse(user, wallet.getBalancePaise(), activeTable);
    }

    public PublicProfileResponse getPublicProfile(String targetUserId) {
        User user = userRepository.findById(targetUserId)
                .filter(u -> u.getAccountStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return UserMapper.toPublicProfileResponse(user);
    }

    public UserProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = findActiveUser(userId);

        if (request.getDisplayName() != null && !request.getDisplayName().isBlank()) {
            String newDisplayName = request.getDisplayName().trim();
            if (!newDisplayName.equals(user.getDisplayName())) {
                if (userRepository.existsByDisplayNameAndIdNot(newDisplayName, userId)) {
                    throw new DuplicateUserException("Display name '" + newDisplayName + "' is already taken.");
                }
                user.setDisplayName(newDisplayName);
            }
        }

        if (request.getAvatarUrl() != null) {
            String avatar = request.getAvatarUrl().trim();
            user.setAvatarUrl(avatar.isEmpty() ? null : avatar);
        }

        User savedUser = userRepository.save(user);
        log.info("Updated profile for user [{}]", userId);
        return buildProfileResponse(savedUser);
    }

    public void changePassword(String userId, ChangePasswordRequest request) {
        User user = findActiveUser(userId);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect.");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from the current password.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed for user [{}]", userId);
    }

    public UserProfileResponse recordHeartbeat(String userId) {
        try {
            userPresenceService.markOnline(userId);
        } catch (Exception ex) {
            log.warn("Heartbeat presence update failed for [{}]: {}", userId, ex.getMessage());
        }
        try {
            return getUserProfile(userId);
        } catch (Exception ex) {
            log.warn("Heartbeat profile build failed for [{}]: {}", userId, ex.getMessage());
            // Never 500 the client for presence polling — return a minimal profile shell.
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                throw ex;
            }
            return UserMapper.toUserProfileResponse(user, user.getWalletBalance(), null);
        }
    }

    public void markOffline(String userId) {
        userPresenceService.markOffline(userId);
    }

    public OnlinePlayersResponse getOnlinePlayersCount() {
        long count = userRepository.countByIsOnlineTrue();
        return OnlinePlayersResponse.builder()
                .onlinePlayers(count > 0 ? (int) count : 1)
                .build();
    }

    public void completeTutorial(String userId) {
        User user = findActiveUser(userId);
        user.setFirstLoginTutorialCompleted(true);
        userRepository.save(user);
        log.info("User [{}] completed first login tutorial", userId);
    }

    public void incrementMatchesPlayed(String userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setMatchesPlayedCount(user.getMatchesPlayedCount() + 1);
            userRepository.save(user);
        });
    }

    private User findActiveUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AccountStatusException("Account is not active.");
        }
        return user;
    }

    private UserProfileResponse buildProfileResponse(User user) {
        WalletBalanceResponse wallet = walletService.getBalance(user.getId());
        UserActiveTableDto activeTable = findActiveTable(user.getId());
        return UserMapper.toUserProfileResponse(user, wallet.getBalancePaise(), activeTable);
    }

    private UserActiveTableDto findActiveTable(String userId) {
        List<Table> seated = tableRepository.findBySeatedPlayerIdsContainingAndStatusIn(
                userId, TableStatusGroups.userActiveList());
        Optional<Table> active = seated.stream().findFirst();
        if (active.isEmpty()) {
            return null;
        }
        Table table = active.get();
        int seatIndex = table.getSeatedPlayerIds() != null
                ? table.getSeatedPlayerIds().indexOf(userId)
                : -1;
        return UserActiveTableDto.builder()
                .tableId(table.getId())
                .tableName(table.getTableName())
                .status(table.getStatus() != null ? table.getStatus().name() : null)
                .tableType(table.getTableType() != null ? table.getTableType().name() : null)
                .seatedCount(table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds().size() : 0)
                .maxPlayers(table.getMaxPlayers())
                .userSeatIndex(seatIndex)
                .host(userId.equals(table.getHostId()))
                .build();
    }
}
