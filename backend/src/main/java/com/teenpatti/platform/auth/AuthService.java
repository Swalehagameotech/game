package com.teenpatti.platform.auth;

import com.teenpatti.platform.auth.dto.*;
import com.teenpatti.platform.common.exception.AccountStatusException;
import com.teenpatti.platform.common.exception.BadCredentialsException;
import com.teenpatti.platform.common.exception.DuplicateUserException;
import com.teenpatti.platform.common.exception.InvalidTokenException;
import com.teenpatti.platform.user.AccountStatus;
import com.teenpatti.platform.user.KycStatus;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.wallet.Wallet;
import com.teenpatti.platform.wallet.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Service encapsulating authentication logic: registration, login, token refresh rotation, and logout.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Atomically registers a new User and initializes an empty Wallet (balancePaise = 0).
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail()) || userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            // Generic duplicate error message to prevent account enumeration
            throw new DuplicateUserException("An account with the provided email or phone number already exists.");
        }

        User user = User.builder()
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName())
                .kycStatus(KycStatus.NOT_STARTED)
                .accountStatus(AccountStatus.ACTIVE)
                .role(com.teenpatti.platform.user.UserRole.PLAYER)
                .build();

        User savedUser = userRepository.save(user);

        // Create zero-balance wallet atomically for new user
        Wallet wallet = Wallet.builder()
                .userId(savedUser.getId())
                .balancePaise(0L)
                .currency("INR")
                .build();
        walletRepository.save(wallet);

        log.info("Registered new user [{}] with atomic wallet initialization", savedUser.getId());
        return createAuthSession(savedUser);
    }

    public AuthResponse login(LoginRequest request) {
        Optional<User> userOpt = userRepository.findByEmail(request.getLoginId());
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByPhoneNumber(request.getLoginId());
        }

        if (userOpt.isEmpty()) {
            throw new BadCredentialsException("Invalid email/phone or password.");
        }

        User user = userOpt.get();

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email/phone or password.");
        }

        if (user.getAccountStatus() == AccountStatus.SUSPENDED) {
            throw new AccountStatusException("Account is suspended. Please contact platform support.");
        }

        if (user.getAccountStatus() == AccountStatus.BANNED) {
            throw new AccountStatusException("Account has been permanently banned.");
        }

        log.info("User [{}] logged in successfully", user.getId());
        return createAuthSession(user);
    }

    /**
     * Validates refresh token and executes refresh token rotation.
     */
    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String rawToken = request.getRefreshToken();
        if (!jwtTokenProvider.validateToken(rawToken, TokenType.REFRESH)) {
            throw new InvalidTokenException("Invalid or expired refresh token.");
        }

        String tokenHash = jwtTokenProvider.hashToken(rawToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Refresh token record not found."));

        if (storedToken.isRevoked() || storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("Refresh token has expired or been revoked.");
        }

        // ROTATION PATTERN: Revoke old refresh token immediately
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        User user = userRepository.findById(storedToken.getUserId())
                .orElseThrow(() -> new InvalidTokenException("User associated with refresh token not found."));

        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AccountStatusException("Account status is not active.");
        }

        log.info("Refreshed auth session with token rotation for user [{}]", user.getId());
        return createAuthSession(user);
    }

    public void logout(LogoutRequest request) {
        String tokenHash = jwtTokenProvider.hashToken(request.getRefreshToken());
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            log.info("Revoked refresh token for user [{}]", token.getUserId());
        });
    }

    private AuthResponse createAuthSession(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String refreshTokenStr = jwtTokenProvider.generateRefreshToken(user.getId());

        String tokenHash = jwtTokenProvider.hashToken(refreshTokenStr);
        Instant refreshExpiry = jwtTokenProvider.getExpirationFromToken(refreshTokenStr).toInstant();

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(tokenHash)
                .expiresAt(refreshExpiry)
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshTokenEntity);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessExpirationMs() / 1000)
                .user(UserProfileDto.fromUser(user))
                .build();
    }
}
