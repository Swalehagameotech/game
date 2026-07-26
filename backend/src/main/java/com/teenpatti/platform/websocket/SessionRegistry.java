package com.teenpatti.platform.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

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
        userSessionMap.put(userId, session);
        log.info("Registered WebSocket session for userId [{}]", userId);
    }

    public void unregisterUserSession(String userId) {
        if (userId == null) return;
        userSessionMap.remove(userId);
        String tableId = userTableMap.remove(userId);
        if (tableId != null) {
            Set<String> connections = tableConnectionMap.get(tableId);
            if (connections != null) {
                connections.remove(userId);
            }
        }
        log.info("Unregistered WebSocket session for userId [{}]", userId);
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
