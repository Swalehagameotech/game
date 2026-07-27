package com.teenpatti.platform.user;

import com.teenpatti.platform.websocket.WebSocketEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Tracks player online presence for lobby statistics and session lifecycle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPresenceService {

    private final UserRepository userRepository;
    private final WebSocketEventPublisher webSocketEventPublisher;

    public void markOnline(String userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setOnline(true);
            user.setLastSeenAt(Instant.now());
            userRepository.save(user);
            webSocketEventPublisher.publishUserStatusChanged(userId, true);
            log.debug("Marked user [{}] online", userId);
        });
    }

    public void markOffline(String userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setOnline(false);
            user.setLastSeenAt(Instant.now());
            userRepository.save(user);
            webSocketEventPublisher.publishUserStatusChanged(userId, false);
            log.debug("Marked user [{}] offline", userId);
        });
    }

    public void touchLastSeen(String userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastSeenAt(Instant.now());
            userRepository.save(user);
        });
    }
}
