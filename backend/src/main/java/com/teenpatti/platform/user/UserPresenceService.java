package com.teenpatti.platform.user;

import com.teenpatti.platform.websocket.WebSocketEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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
    private final MongoTemplate mongoTemplate;

    public void markOnline(String userId) {
        try {
            User existing = userRepository.findById(userId).orElse(null);
            boolean wasOffline = existing == null || !existing.isOnline();

            // Version-less update avoids heartbeat storms causing OptimisticLockingFailureException 500s.
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(userId)),
                    new Update().set("isOnline", true).set("lastSeenAt", Instant.now()),
                    User.class
            );

            if (wasOffline) {
                webSocketEventPublisher.publishUserStatusChanged(userId, true);
            }
            log.debug("Marked user [{}] online", userId);
        } catch (OptimisticLockingFailureException ex) {
            log.debug("Presence online update raced for user [{}] — ignored", userId);
        } catch (Exception ex) {
            log.warn("Failed to mark user [{}] online: {}", userId, ex.getMessage());
        }
    }

    public void markOffline(String userId) {
        try {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(userId)),
                    new Update().set("isOnline", false).set("lastSeenAt", Instant.now()),
                    User.class
            );
            webSocketEventPublisher.publishUserStatusChanged(userId, false);
            log.debug("Marked user [{}] offline", userId);
        } catch (Exception ex) {
            log.warn("Failed to mark user [{}] offline: {}", userId, ex.getMessage());
        }
    }

    public void touchLastSeen(String userId) {
        try {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(userId)),
                    new Update().set("lastSeenAt", Instant.now()),
                    User.class
            );
        } catch (Exception ex) {
            log.debug("touchLastSeen failed for [{}]: {}", userId, ex.getMessage());
        }
    }
}
