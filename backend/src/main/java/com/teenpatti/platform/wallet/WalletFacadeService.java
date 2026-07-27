package com.teenpatti.platform.wallet;

import com.teenpatti.platform.transaction.DepositService;
import com.teenpatti.platform.transaction.LedgerEntry;
import com.teenpatti.platform.transaction.LedgerEntryType;
import com.teenpatti.platform.transaction.WithdrawalRequest;
import com.teenpatti.platform.transaction.WithdrawalRequestRepository;
import com.teenpatti.platform.transaction.WithdrawalService;
import com.teenpatti.platform.transaction.WithdrawalStatus;
import com.teenpatti.platform.transaction.dto.DepositInitiationResponse;
import com.teenpatti.platform.transaction.dto.DepositResponse;
import com.teenpatti.platform.transaction.dto.InitiateWithdrawalRequest;
import com.teenpatti.platform.transaction.dto.WithdrawalResponse;
import com.teenpatti.platform.wallet.dto.WalletBalanceResponse;
import com.teenpatti.platform.wallet.dto.WalletDepositRequest;
import com.teenpatti.platform.wallet.dto.WalletDepositResponse;
import com.teenpatti.platform.wallet.dto.WalletSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Application facade for user wallet operations (deposit, withdraw, summary).
 */
@Service
@RequiredArgsConstructor
public class WalletFacadeService {

    private final WalletService walletService;
    private final DepositService depositService;
    private final WithdrawalService withdrawalService;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;

    @Value("${app.deposit.min-amount-paise:10000}")
    private long minDepositPaise;

    @Value("${app.deposit.max-amount-paise:5000000}")
    private long maxDepositPaise;

    @Value("${app.withdrawal.min-amount-paise:50000}")
    private long minWithdrawalPaise;

    @Value("${app.withdrawal.max-amount-paise:10000000}")
    private long maxWithdrawalPaise;

    @Value("${app.wallet.demo-deposit-enabled:true}")
    private boolean demoDepositEnabled;

    public WalletSummaryResponse getSummary(String userId) {
        WalletBalanceResponse balance = walletService.getBalance(userId);
        long deposited = sumLedger(userId, LedgerEntryType.DEPOSIT, LedgerEntryType.BONUS, LedgerEntryType.ADMIN_ADJUSTMENT);
        long withdrawn = sumLedger(userId, LedgerEntryType.WITHDRAWAL);
        long pendingWithdrawal = withdrawalRequestRepository.findByUserIdAndStatus(userId, WithdrawalStatus.PENDING_ADMIN_REVIEW)
                .stream()
                .mapToLong(WithdrawalRequest::getAmountPaise)
                .sum();

        return WalletSummaryResponse.builder()
                .userId(userId)
                .balancePaise(balance.getBalancePaise())
                .formattedBalance(String.format("₹%.2f", balance.getBalancePaise() / 100.0))
                .currency(balance.getCurrency())
                .totalDepositedPaise(deposited)
                .totalWithdrawnPaise(withdrawn)
                .pendingWithdrawalPaise(pendingWithdrawal)
                .minDepositPaise(minDepositPaise)
                .maxDepositPaise(maxDepositPaise)
                .minWithdrawalPaise(minWithdrawalPaise)
                .maxWithdrawalPaise(maxWithdrawalPaise)
                .build();
    }

    public WalletDepositResponse deposit(String userId, WalletDepositRequest request) {
        long amountPaise = request.getAmountPaise();
        if (request.isDemo() || demoDepositEnabled) {
            WalletBalanceResponse balance = walletService.depositDemoChips(userId, amountPaise);
            return WalletDepositResponse.builder()
                    .demoCredited(true)
                    .amountPaise(amountPaise)
                    .balancePaise(balance.getBalancePaise())
                    .formattedBalance(String.format("₹%.2f", balance.getBalancePaise() / 100.0))
                    .build();
        }

        DepositInitiationResponse gateway = depositService.initiateDeposit(userId, amountPaise);
        WalletBalanceResponse balance = walletService.getBalance(userId);
        return WalletDepositResponse.builder()
                .demoCredited(false)
                .amountPaise(amountPaise)
                .balancePaise(balance.getBalancePaise())
                .formattedBalance(String.format("₹%.2f", balance.getBalancePaise() / 100.0))
                .gatewayOrder(gateway)
                .build();
    }

    public WalletDepositResponse completeGatewayDeposit(String userId, String depositRequestId) {
        WalletBalanceResponse balance = depositService.completeDepositForUser(userId, depositRequestId);
        var dep = depositService.getDepositRequest(userId, depositRequestId);
        return WalletDepositResponse.builder()
                .demoCredited(true)
                .amountPaise(dep.getAmountPaise())
                .balancePaise(balance.getBalancePaise())
                .formattedBalance(String.format("₹%.2f", balance.getBalancePaise() / 100.0))
                .build();
    }

    public WithdrawalResponse withdraw(String userId, InitiateWithdrawalRequest request) {
        return withdrawalService.requestWithdrawal(userId, request);
    }

    private long sumLedger(String userId, LedgerEntryType... types) {
        Set<LedgerEntryType> typeSet = Set.of(types);
        return ledgerEntryRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 1000))
                .stream()
                .filter(e -> e.getType() != null && typeSet.contains(e.getType()))
                .mapToLong(e -> {
                    if (e.getType() == LedgerEntryType.WITHDRAWAL) {
                        return Math.abs(e.getAmountPaise());
                    }
                    return Math.max(e.getAmountPaise(), 0L);
                })
                .sum();
    }
}
