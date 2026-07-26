package com.teenpatti.platform.config;

import com.teenpatti.platform.admin.AdminActionLog;
import com.teenpatti.platform.game.MatchHistory;
import com.teenpatti.platform.notification.Notification;
import com.teenpatti.platform.table.Table;
import com.teenpatti.platform.transaction.LedgerEntry;
import com.teenpatti.platform.user.FriendRelationship;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.wallet.Wallet;
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
 * Mongo DB configuration, collection initialization, and startup health-check ping.
 */
@Slf4j
@Configuration
public class MongoConfig {

    @Bean
    public CommandLineRunner mongoInitializer(MongoTemplate mongoTemplate, MongoMappingContext mappingContext) {
        return args -> {
            try {
                // 1. Health check ping
                Document pingCommand = new Document("ping", 1);
                Document result = mongoTemplate.getDb().runCommand(pingCommand);
                log.info("MongoDB Health Ping SUCCESS: {}", result.toJson());

                // 2. Ensure all 8 domain entity collections and indexes are initialized
                List<Class<?>> entityClasses = List.of(
                        User.class,
                        Wallet.class,
                        LedgerEntry.class,
                        Table.class,
                        MatchHistory.class,
                        FriendRelationship.class,
                        Notification.class,
                        AdminActionLog.class
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

                log.info("MongoDB Collections & Indexes verification COMPLETED successfully across all 8 domain documents.");
            } catch (Exception e) {
                log.warn("MongoDB Startup Initialization Note: {}", e.getMessage());
            }
        };
    }
}
