package com.teenpatti.platform.websocket;

import com.teenpatti.platform.auth.JwtTokenProvider;
import com.teenpatti.platform.auth.TokenType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

/**
 * Handshake interceptor extracting and validating JWT tokens from WebSocket connection query parameters.
 * Binds authenticated userId to session attributes for connection lifetime.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) throws Exception {

        URI uri = request.getURI();
        String query = uri.getQuery();
        String token = extractTokenFromQuery(query);

        if (token == null || token.isBlank()) {
            log.warn("WebSocket handshake rejected: Token query parameter missing");
            return false;
        }

        boolean isValid = jwtTokenProvider.validateToken(token, TokenType.ACCESS);
        if (!isValid) {
            log.warn("WebSocket handshake rejected: Invalid or expired ACCESS JWT token");
            return false;
        }

        String userId = jwtTokenProvider.getUserIdFromToken(token);
        attributes.put("userId", userId);
        log.info("WebSocket handshake authenticated successfully for userId [{}]", userId);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // Post-handshake processing if needed
    }

    private String extractTokenFromQuery(String query) {
        if (query == null || query.isBlank()) return null;
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length == 2 && "token".equals(pair[0])) {
                return pair[1];
            }
        }
        return null;
    }
}
