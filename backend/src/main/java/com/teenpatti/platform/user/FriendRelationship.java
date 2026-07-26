package com.teenpatti.platform.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * FriendRelationship document representing social connections and friend requests.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "friend_relationships")
@CompoundIndex(name = "user_friend_idx", def = "{'userId': 1, 'friendUserId': 1}", unique = true)
public class FriendRelationship {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String friendUserId;

    @Builder.Default
    private FriendshipStatus status = FriendshipStatus.PENDING;

    @CreatedDate
    private Instant createdAt;
}
