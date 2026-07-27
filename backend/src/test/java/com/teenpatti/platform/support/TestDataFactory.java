package com.teenpatti.platform.support;

import com.teenpatti.platform.auth.JwtTokenProvider;
import com.teenpatti.platform.user.AccountStatus;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.user.UserRole;
import com.teenpatti.platform.wallet.Wallet;
import com.teenpatti.platform.wallet.WalletRepository;

/**
 * Shared helpers for integration and controller tests.
 */
public final class TestDataFactory {

    private TestDataFactory() {
    }

    public record TestUserContext(User user, Wallet wallet, String accessToken) {
    }

    public static TestUserContext createPlayer(
            UserRepository userRepository,
            WalletRepository walletRepository,
            JwtTokenProvider jwtTokenProvider,
            String email,
            String displayName,
            long balancePaise) {
        User user = userRepository.save(User.builder()
                .email(email)
                .phoneNumber(String.format("9%09d", Math.abs(email.hashCode()) % 1_000_000_000))
                .passwordHash("test-hash")
                .displayName(displayName)
                .accountStatus(AccountStatus.ACTIVE)
                .role(UserRole.PLAYER)
                .build());

        Wallet wallet = walletRepository.save(Wallet.builder()
                .userId(user.getId())
                .balancePaise(balancePaise)
                .currency("INR")
                .build());

        String token = jwtTokenProvider.generateAccessToken(user.getId());
        return new TestUserContext(user, wallet, token);
    }

    public static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
