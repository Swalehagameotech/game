package com.teenpatti.platform.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisDistributedLock {

    private final StringRedisTemplate redisTemplate;
    private final String instanceId = UUID.randomUUID().toString();

    public boolean tryLock(String lockKey, long leaseTimeMillis) {
        if (redisTemplate == null) {
            return true; // Fallback when Redis is omitted in unit test environment
        }
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, instanceId, Duration.ofMillis(leaseTimeMillis));
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.warn("Redis distributed lock acquisition failed for key [{}]: {}. Fallback granted.", lockKey, e.getMessage());
            return true;
        }
    }

    public void unlock(String lockKey) {
        if (redisTemplate == null) return;
        try {
            String currentValue = redisTemplate.opsForValue().get(lockKey);
            if (instanceId.equals(currentValue)) {
                redisTemplate.delete(lockKey);
            }
        } catch (Exception e) {
            log.warn("Redis distributed lock release failed for key [{}]: {}", lockKey, e.getMessage());
        }
    }
}
