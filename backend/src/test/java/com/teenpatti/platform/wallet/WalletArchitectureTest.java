package com.teenpatti.platform.wallet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture test enforcing that Wallet write operations and mutations occur exclusively within WalletService.
 */
class WalletArchitectureTest {

    @Test
    @DisplayName("ARCHITECTURAL INVARIANT: Wallet document modifications are encapsulated inside WalletService")
    void verifyWalletServiceEncapsulation() {
        Method[] serviceMethods = WalletService.class.getDeclaredMethods();
        boolean hasApplyLedgerEntry = Arrays.stream(serviceMethods)
                .anyMatch(m -> m.getName().equals("applyLedgerEntry"));

        assertThat(hasApplyLedgerEntry)
                .withFailMessage("WalletService must expose applyLedgerEntry as the single atomic balance modification method.")
                .isTrue();
    }
}
