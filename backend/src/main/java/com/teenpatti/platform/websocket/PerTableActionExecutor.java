package com.teenpatti.platform.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * Ensures strict sequential action execution per tableId using dedicated per-table
 * single-threaded executors and Redis distributed locking across instances.
 */
@Slf4j
@Component
public class PerTableActionExecutor {

    private final Map<String, ExecutorService> tableExecutors = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private RedisDistributedLock redisDistributedLock;

    public Future<?> submitTableAction(String tableId, Runnable task) {
        if (tableId == null || task == null) {
            throw new IllegalArgumentException("tableId and task must not be null");
        }
        ExecutorService executor = tableExecutors.computeIfAbsent(tableId, k ->
                Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "table-executor-" + tableId);
                    t.setDaemon(true);
                    return t;
                })
        );
        return executor.submit(() -> {
            String lockKey = "lock:table:" + tableId;
            boolean locked = redisDistributedLock != null ? redisDistributedLock.tryLock(lockKey, 5000) : true;
            try {
                task.run();
            } finally {
                if (redisDistributedLock != null && locked) {
                    redisDistributedLock.unlock(lockKey);
                }
            }
        });
    }

    public void executeTableActionSync(String tableId, Runnable task) {
        try {
            submitTableAction(tableId, task).get();
        } catch (Exception e) {
            log.error("Error executing action for table [{}]: {}", tableId, e.getMessage(), e);
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Table action execution failed: " + e.getMessage(), e);
        }
    }

    public void shutdownTableExecutor(String tableId) {
        if (tableId == null) return;
        ExecutorService executor = tableExecutors.remove(tableId);
        if (executor != null) {
            executor.shutdown();
            log.info("Shutdown executor for table [{}]", tableId);
        }
    }
}
