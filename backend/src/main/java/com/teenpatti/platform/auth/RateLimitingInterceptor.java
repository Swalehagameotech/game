package com.teenpatti.platform.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory sliding window rate limiter for authentication endpoints (/api/auth/register, /api/auth/login).
 *
 * CRITICAL HORIZONTAL SCALING NOTE:
 * This in-memory ConcurrentHashMap implementation is designed for single-node development and testing.
 * It DOES NOT synchronize state across multiple application server instances.
 * In Phase 16 (Redis Infrastructure), this rate limiter MUST be replaced with a distributed Redis sliding-window counter.
 */
@Slf4j
@Component
public class RateLimitingInterceptor implements HandlerInterceptor {

    private static final int MAX_REQUESTS_PER_MINUTE = 15;
    private static final long ONE_MINUTE_MS = 60_000L;

    private final Map<String, RequestCounter> requestCounts = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientIp = getClientIp(request);
        long now = System.currentTimeMillis();

        RequestCounter counter = requestCounts.compute(clientIp, (ip, current) -> {
            if (current == null || (now - current.startTime) > ONE_MINUTE_MS) {
                return new RequestCounter(now, new AtomicInteger(1));
            }
            current.count.incrementAndGet();
            return current;
        });

        if (counter.count.get() > MAX_REQUESTS_PER_MINUTE) {
            log.warn("Rate limit exceeded for IP [{}] on URI [{}]", clientIp, request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"errorCode\":\"TOO_MANY_REQUESTS\",\"message\":\"Too many requests. Please try again in 60 seconds.\"}");
            return false;
        }

        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class RequestCounter {
        final long startTime;
        final AtomicInteger count;

        RequestCounter(long startTime, AtomicInteger count) {
            this.startTime = startTime;
            this.count = count;
        }
    }
}
