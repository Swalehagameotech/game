package com.teenpatti.platform.transaction;

import com.teenpatti.platform.common.exception.InvalidTransactionAmountException;
import com.teenpatti.platform.common.exception.UserNotFoundException;
import com.teenpatti.platform.transaction.dto.DepositInitiationResponse;
import com.teenpatti.platform.transaction.dto.DepositResponse;
import com.teenpatti.platform.transaction.gateway.GatewayOrder;
import com.teenpatti.platform.transaction.gateway.PaymentGatewayClient;
import com.teenpatti.platform.transaction.LedgerEntryType;
import com.teenpatti.platform.wallet.WalletService;
import com.teenpatti.platform.wallet.dto.WalletBalanceResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class DepositService {

    private final DepositRequestRepository depositRequestRepository;
    private final PaymentGatewayClient paymentGatewayClient;
    private final WalletService walletService;
    private final long minDepositAmountPaise;
    private final long maxDepositAmountPaise;

    public DepositService(
            DepositRequestRepository depositRequestRepository,
            PaymentGatewayClient paymentGatewayClient,
            WalletService walletService,
            @Value("${app.deposit.min-amount-paise:10000}") long minDepositAmountPaise,
            @Value("${app.deposit.max-amount-paise:5000000}") long maxDepositAmountPaise) {
        this.depositRequestRepository = depositRequestRepository;
        this.paymentGatewayClient = paymentGatewayClient;
        this.walletService = walletService;
        this.minDepositAmountPaise = minDepositAmountPaise;
        this.maxDepositAmountPaise = maxDepositAmountPaise;
    }

    public DepositInitiationResponse initiateDeposit(String userId, long amountPaise) {
        if (amountPaise < minDepositAmountPaise || amountPaise > maxDepositAmountPaise) {
            log.warn("Deposit request amount [{}] paise outside configured limits [{} - {}] paise",
                    amountPaise, minDepositAmountPaise, maxDepositAmountPaise);
            throw new InvalidTransactionAmountException(
                    "Deposit amount must be between " + minDepositAmountPaise + " paise (₹" + (minDepositAmountPaise / 100) +
                            ") and " + maxDepositAmountPaise + " paise (₹" + (maxDepositAmountPaise / 100) + ")."
            );
        }

        String receiptId = "dep_rcpt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        GatewayOrder gatewayOrder = paymentGatewayClient.createOrder(amountPaise, receiptId);

        DepositRequest depositRequest = DepositRequest.builder()
                .userId(userId)
                .gatewayOrderId(gatewayOrder.getOrderId())
                .amountPaise(amountPaise)
                .status(DepositStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        DepositRequest savedRequest = depositRequestRepository.save(depositRequest);
        log.info("Created PENDING DepositRequest [{}] with gatewayOrderId [{}] for user [{}]",
                savedRequest.getId(), gatewayOrder.getOrderId(), userId);

        return DepositInitiationResponse.builder()
                .depositRequestId(savedRequest.getId())
                .gatewayOrderId(gatewayOrder.getOrderId())
                .amountPaise(amountPaise)
                .currency(gatewayOrder.getCurrency())
                .keyId(gatewayOrder.getKeyId())
                .build();
    }

    public DepositResponse getDepositRequest(String userId, String depositRequestId) {
        DepositRequest depositRequest = depositRequestRepository.findById(depositRequestId)
                .orElseThrow(() -> new UserNotFoundException("Deposit request not found: " + depositRequestId));

        if (!depositRequest.getUserId().equals(userId)) {
            throw new UserNotFoundException("Deposit request not found: " + depositRequestId);
        }

        return toDepositResponse(depositRequest);
    }

    /**
     * Completes a pending deposit (dev/mock gateway or manual confirmation).
     */
    public WalletBalanceResponse completeDepositForUser(String userId, String depositRequestId) {
        DepositRequest depositRequest = depositRequestRepository.findById(depositRequestId)
                .orElseThrow(() -> new UserNotFoundException("Deposit request not found: " + depositRequestId));

        if (!depositRequest.getUserId().equals(userId)) {
            throw new UserNotFoundException("Deposit request not found: " + depositRequestId);
        }

        if (depositRequest.getStatus() == DepositStatus.COMPLETED) {
            return walletService.getBalance(userId);
        }

        if (depositRequest.getStatus() != DepositStatus.PENDING) {
            throw new IllegalStateException("Deposit request is not pending: " + depositRequest.getStatus());
        }

        String referenceId = "deposit:" + depositRequest.getGatewayOrderId();
        walletService.applyLedgerEntry(
                depositRequest.getUserId(),
                LedgerEntryType.DEPOSIT,
                depositRequest.getAmountPaise(),
                referenceId
        );

        depositRequest.setStatus(DepositStatus.COMPLETED);
        depositRequest.setGatewayPaymentId("mock_pay_" + depositRequestId);
        depositRequest.setCompletedAt(Instant.now());
        depositRequestRepository.save(depositRequest);

        log.info("Manually completed deposit [{}] for user [{}], amount {} paise",
                depositRequestId, userId, depositRequest.getAmountPaise());

        return walletService.getBalance(userId);
    }

    private DepositResponse toDepositResponse(DepositRequest request) {
        return DepositResponse.builder()
                .id(request.getId())
                .userId(request.getUserId())
                .gatewayOrderId(request.getGatewayOrderId())
                .gatewayPaymentId(request.getGatewayPaymentId())
                .amountPaise(request.getAmountPaise())
                .status(request.getStatus())
                .createdAt(request.getCreatedAt())
                .completedAt(request.getCompletedAt())
                .build();
    }
}
