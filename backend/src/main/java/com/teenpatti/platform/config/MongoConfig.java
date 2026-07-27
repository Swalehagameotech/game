package com.teenpatti.platform.config;

import com.teenpatti.platform.admin.AdminActionLog;
import com.teenpatti.platform.admin.AdminLog;
import com.teenpatti.platform.auth.RefreshToken;
import com.teenpatti.platform.game.GameHistory;
import com.teenpatti.platform.game.GameSession;
import com.teenpatti.platform.game.MatchHistory;
import com.teenpatti.platform.notification.Notification;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.transaction.DepositRequest;
import com.teenpatti.platform.transaction.LedgerEntry;
import com.teenpatti.platform.transaction.WithdrawalRequest;
import com.teenpatti.platform.user.FriendRelationship;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.wallet.Wallet;
import com.teenpatti.platform.wallet.WalletTransaction;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.List;

/**
 * MongoDB configuration: health ping, collection bootstrap, and index enforcement
 * for all platform domain documents defined in Module 2 (MongoDB Schemas).
 */
@Slf4j
@Configuration
public class MongoConfig {

    @Bean
    public CommandLineRunner mongoInitializer(MongoTemplate mongoTemplate, MongoMappingContext mappingContext) {
        return args -> {
            try {
                Document pingCommand = new Document("ping", 1);
                Document result = mongoTemplate.getDb().runCommand(pingCommand);
                log.info("MongoDB Health Ping SUCCESS: {}", result.toJson());

                List<Class<?>> entityClasses = List.of(
                        User.class,
                        Wallet.class,
                        WalletTransaction.class,
                        LedgerEntry.class,
                        Table.class,
                        GameSession.class,
                        GameHistory.class,
                        MatchHistory.class,
                        Notification.class,
                        AdminLog.class,
                        AdminActionLog.class,
                        FriendRelationship.class,
                        RefreshToken.class,
                        DepositRequest.class,
                        WithdrawalRequest.class
                );

                IndexResolver resolver = new MongoPersistentEntityIndexResolver(mappingContext);

                for (Class<?> entityClass : entityClasses) {
                    if (!mongoTemplate.collectionExists(entityClass)) {
                        mongoTemplate.createCollection(entityClass);
                        log.info("Created collection for entity: {}", entityClass.getSimpleName());
                    }
                    var indexOps = mongoTemplate.indexOps(entityClass);
                    if (entityClass == LedgerEntry.class) {
                        try {
                            indexOps.getIndexInfo().stream()
                                    .filter(idx -> "referenceId".equals(idx.getName()) && !idx.isUnique())
                                    .findFirst()
                                    .ifPresent(idx -> {
                                        log.info("Dropping legacy non-unique index 'referenceId' on ledger_entries");
                                        indexOps.dropIndex("referenceId");
                                    });
                        } catch (Exception ex) {
                            log.warn("Legacy index drop note: {}", ex.getMessage());
                        }
                    }
                    resolver.resolveIndexFor(entityClass).forEach(indexDef -> {
                        try {
                            indexOps.ensureIndex(indexDef);
                        } catch (Exception idxEx) {
                            log.warn("Index creation note for entity {}: {}", entityClass.getSimpleName(), idxEx.getMessage());
                        }
                    });
                }

                log.info("MongoDB collections & indexes verified for {} domain documents.", entityClasses.size());
            } catch (Exception e) {
                log.warn("MongoDB startup initialization note: {}", e.getMessage());
            }
        };
    }
}
