package com.teenpatti.platform.table;

import com.teenpatti.platform.common.exception.TableFullException;
import com.teenpatti.platform.common.exception.TableNotFoundException;
import com.teenpatti.platform.notification.NotificationRepository;
import com.teenpatti.platform.notification.NotificationService;
import com.teenpatti.platform.notification.NotificationType;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Private-table business rules: invite-code lookup, join eligibility, and host-only start.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrivateTableService {

    public static final Set<TableStatus> JOINABLE_STATUSES = EnumSet.of(
            TableStatus.WAITING,
            TableStatus.ROUND_END
    );

    private final TableRepository tableRepository;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public boolean isPrivateTable(Table table) {
        return table != null && table.getTableType() == TableType.PRIVATE;
    }

    public String normalizeInviteCode(String inviteCode) {
        if (inviteCode == null) {
            return "";
        }
        return inviteCode.trim().toUpperCase();
    }

    public Table resolvePrivateTable(String inviteCode) {
        String normalized = normalizeInviteCode(inviteCode);
        if (normalized.isBlank()) {
            throw new TableNotFoundException("Private table not found or invalid invite code.");
        }

        Table table = tableRepository.findByInviteCode(normalized)
                .orElseThrow(() -> new TableNotFoundException("Private table not found or invalid invite code."));

        if (table.getTableType() != TableType.PRIVATE || table.getStatus() == TableStatus.CLOSED) {
            throw new TableNotFoundException("Private table not found or invalid invite code.");
        }

        return table;
    }

    public void assertJoinable(Table table) {
        if (table == null) {
            throw new TableNotFoundException("Table not found");
        }
        if (!isPrivateTable(table)) {
            return;
        }
        if (table.getStatus() == TableStatus.CLOSED) {
            throw new TableNotFoundException("Private table is closed");
        }
        if (TableStatusGroups.isRunning(table.getStatus()) || table.getStatus() == TableStatus.STARTING) {
            throw new TableFullException("Game already in progress on this private table.");
        }
        if (!JOINABLE_STATUSES.contains(table.getStatus())) {
            throw new TableFullException(
                    "Private table is not open for joining. Status: " + table.getStatus());
        }
        int seated = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds().size() : 0;
        if (seated >= table.getMaxPlayers()) {
            throw new TableFullException("Private table is full (max players: " + table.getMaxPlayers() + ")");
        }
    }

    public void assertHostCanStart(Table table, String userId) {
        if (!isPrivateTable(table)) {
            return;
        }
        if (userId == null || table.getHostId() == null || !table.getHostId().equals(userId)) {
            throw new IllegalStateException("Only the table host can start a private table.");
        }
        if (!JOINABLE_STATUSES.contains(table.getStatus())) {
            throw new IllegalStateException(
                    "Private table cannot be started in status: " + table.getStatus());
        }
        int seated = table.getSeatedPlayerIds() != null ? table.getSeatedPlayerIds().size() : 0;
        int minRequired = table.getMinPlayers() > 0 ? table.getMinPlayers() : 3;
        if (seated < minRequired) {
            throw new IllegalStateException(
                    "Minimum " + minRequired + " players required. Currently seated: " + seated);
        }
    }

    public void sendInviteNotifications(Table table, String hostUserId, List<String> inviteUserIds) {
        if (inviteUserIds == null || inviteUserIds.isEmpty() || table == null) {
            return;
        }

        String hostName = userRepository.findById(hostUserId)
                .map(User::getDisplayName)
                .filter(n -> n != null && !n.isBlank())
                .orElse("Host");

        Map<String, Object> payload = new HashMap<>();
        payload.put("tableId", table.getId());
        payload.put("inviteCode", table.getInviteCode());
        payload.put("hostId", hostUserId);
        payload.put("hostDisplayName", hostName);

        String message = hostName + " invited you to a private Teen Patti table. Code: " + table.getInviteCode();

        for (String inviteeId : inviteUserIds) {
            if (inviteeId == null || inviteeId.isBlank() || inviteeId.equals(hostUserId)) {
                continue;
            }
            if (!userRepository.existsById(inviteeId)) {
                log.warn("Skipping private invite — user [{}] not found", inviteeId);
                continue;
            }
            notificationService.notify(
                    inviteeId,
                    NotificationType.GAME_INVITE,
                    "Private Table Invite",
                    message,
                    payload
            );
        }
    }

    public void markInviteNotificationsRead(String userId, String tableId, String inviteCode) {
        if (userId == null) {
            return;
        }
        String normalizedCode = normalizeInviteCode(inviteCode);
        List<com.teenpatti.platform.notification.Notification> invites =
                notificationRepository.findByUserIdAndTypeAndIsReadFalseOrderByCreatedAtDesc(
                        userId, NotificationType.GAME_INVITE);

        for (com.teenpatti.platform.notification.Notification notification : invites) {
            Map<String, Object> payload = notification.getPayload() != null
                    ? notification.getPayload()
                    : Map.of();
            String payloadTableId = payload.get("tableId") != null ? payload.get("tableId").toString() : null;
            String payloadCode = payload.get("inviteCode") != null
                    ? normalizeInviteCode(payload.get("inviteCode").toString())
                    : "";

            if ((tableId != null && tableId.equals(payloadTableId))
                    || (!normalizedCode.isBlank() && normalizedCode.equals(payloadCode))) {
                notification.setRead(true);
                notificationRepository.save(notification);
            }
        }
    }
}
