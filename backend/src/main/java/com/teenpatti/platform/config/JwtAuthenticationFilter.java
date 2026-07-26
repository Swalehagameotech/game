package com.teenpatti.platform.config;

import com.teenpatti.platform.auth.JwtTokenProvider;
import com.teenpatti.platform.auth.TokenType;
import com.teenpatti.platform.user.AccountStatus;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Filter that intercepts incoming HTTP requests, extracts JWT access tokens from the Authorization header,
 * validates token integrity and ACCESS claim type, resolves user identity & role, and populates Spring SecurityContext.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends org.springframework.web.filter.OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return path.equals("/api/auth/register") || path.equals("/api/auth/login") || path.equals("/api/auth/refresh");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();

            if (jwtTokenProvider.validateToken(token, TokenType.ACCESS)) {
                String userId = jwtTokenProvider.getUserIdFromToken(token);
                Optional<User> userOpt = userRepository.findById(userId);

                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    if (user.getAccountStatus() == AccountStatus.ACTIVE) {
                        String roleName = "ROLE_" + (user.getRole() != null ? user.getRole().name() : "PLAYER");
                        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(roleName));

                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                user.getId(),
                                null,
                                authorities
                        );

                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } else {
                        log.warn("Access attempt by non-active user [{}]", userId);
                    }
                }
            } else {
                log.warn("Invalid or non-ACCESS JWT token provided on request [{}]", request.getRequestURI());
            }
        }

        filterChain.doFilter(request, response);
    }
}
