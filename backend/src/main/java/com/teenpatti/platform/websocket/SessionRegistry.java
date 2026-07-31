package com.teenpatti.platform.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry maintaining mappings between authenticated user IDs,
 * active WebSocket sessions, and table connection sets.
 */
@Slf4j
@Component
public class SessionRegistry {

    private final Map<String, WebSocketSession> userSessionMap = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> tableConnectionMap = new ConcurrentHashMap<>();
    private final Map<String, String> userTableMap = new ConcurrentHashMap<>();

    public void registerUserSession(String userId, WebSocketSession session) {
        if (userId == null || session == null) return;
        WebSocketSession previous = userSessionMap.put(userId, session);
        if (previous != null && previous.isOpen() && !Objects.equals(previous.getId(), session.getId())) {
            try {
                previous.close();
            } catch (IOException e) {
                log.debug("Failed closing previous WS session for [{}]: {}", userId, e.getMessage());
            }
        }
        log.info("Registered WebSocket session for userId [{}]", userId);
    }

    /**
     * Unregister only if {@code session} is still the mapped session.
     * Prevents a stale close (reconnect race) from wiping the newer live socket —
     * which was silently dropping SHOW_REQUEST / WINNER_DECLARED deliveries.
     */
    public void unregisterUserSession(String userId, WebSocketSession session) {
        if (userId == null) return;

        userSessionMap.compute(userId, (id, existing) -> {
            if (existing == null) {
                return null;
            }
            if (session == null || Objects.equals(existing.getId(), session.getId())) {
                return null; // remove
            }
            // Newer session already registered — keep it.
            return existing;
        });

        // Only detach table mapping when this user has no open session left.
        if (!isUserConnected(userId)) {
            String tableId = userTableMap.remove(userId);
            if (tableId != null) {
                Set<String> connections = tableConnectionMap.get(tableId);
                if (connections != null) {
                    connections.remove(userId);
                }
            }
            log.info("Unregistered WebSocket session for userId [{}]", userId);
        } else {
            log.info("Ignored stale WS close for userId [{}] — newer session still active", userId);
        }
    }

    /** @deprecated Prefer {@link #unregisterUserSession(String, WebSocketSession)} */
    public void unregisterUserSession(String userId) {
        unregisterUserSession(userId, null);
    }

    public void attachUserToTable(String userId, String tableId) {
        if (userId == null || tableId == null) return;
        userTableMap.put(userId, tableId);
        tableConnectionMap.computeIfAbsent(tableId, k -> ConcurrentHashMap.newKeySet()).add(userId);
        log.info("Attached userId [{}] to live WebSocket session set for table [{}]", userId, tableId);
    }

    public void detachUserFromTable(String userId, String tableId) {
        if (userId == null || tableId == null) return;
        userTableMap.remove(userId, tableId);
        Set<String> connections = tableConnectionMap.get(tableId);
        if (connections != null) {
            connections.remove(userId);
        }
        log.info("Detached userId [{}] from table [{}]", userId, tableId);
    }

    public Set<String> getConnectedUsersForTable(String tableId) {
        if (tableId == null) return Collections.emptySet();
        Set<String> connections = tableConnectionMap.get(tableId);
        return connections != null ? Collections.unmodifiableSet(new HashSet<>(connections)) : Collections.emptySet();
    }

    public WebSocketSession getWebSocketSession(String userId) {
        if (userId == null) return null;
        return userSessionMap.get(userId);
    }

    public String getTableIdForUser(String userId) {
        if (userId == null) return null;
        return userTableMap.get(userId);
    }

    public boolean isUserConnected(String userId) {
        WebSocketSession session = getWebSocketSession(userId);
        return session != null && session.isOpen();
    }
}
