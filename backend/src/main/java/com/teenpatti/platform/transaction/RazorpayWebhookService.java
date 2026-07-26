package com.teenpatti.platform.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenpatti.platform.common.exception.InvalidWebhookSignatureException;
import com.teenpatti.platform.transaction.gateway.PaymentGatewayClient;
import com.teenpatti.platform.wallet.WalletService;
import com.teenpatti.platform.notification.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Isolated service dedicated to Razorpay webhook signature verification,
 * event payload parsing, and idempotent wallet crediting.
 */
@Slf4j
@Service
public class RazorpayWebhookService {

    private final DepositRequestRepository depositRequestRepository;
    private final WalletService walletService;
    private final PaymentGatewayClient paymentGatewayClient;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final String webhookSecret;

    public RazorpayWebhookService(
            DepositRequestRepository depositRequestRepository,
            WalletService walletService,
            PaymentGatewayClient paymentGatewayClient,
            ObjectMapper objectMapper,
            NotificationService notificationService,
            @Value("${razorpay.webhook-secret:dummyWebhookSecret}") String webhookSecret) {
        this.depositRequestRepository = depositRequestRepository;
        this.walletService = walletService;
        this.paymentGatewayClient = paymentGatewayClient;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.webhookSecret = webhookSecret;
    }

    /**
     * Processes an incoming raw webhook request body and signature from Razorpay.
     *
     * @param payload Request body JSON payload string
     * @param signature X-Razorpay-Signature header value
     */
    public void processWebhook(String payload, String signature) {
        // STEP 1: Verify webhook signature
        boolean isValidSignature = paymentGatewayClient.verifyWebhookSignature(payload, signature, webhookSecret);
        if (!isValidSignature) {
            log.warn("SECURITY WARNING: Webhook signature verification failed for signature [{}]", signature);
            throw new InvalidWebhookSignatureException("Invalid or tampered Razorpay webhook signature.");
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String event = root.path("event").asText("");
            log.info("Received verified Razorpay webhook event: [{}]", event);

            JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
            String gatewayOrderId = paymentEntity.path("order_id").asText(null);
            String gatewayPaymentId = paymentEntity.path("id").asText(null);

            if (gatewayOrderId == null || gatewayOrderId.isBlank()) {
                log.warn("Webhook payload missing order_id field. Event: [{}]", event);
                return;
            }

            Optional<DepositRequest> depositOpt = depositRequestRepository.findByGatewayOrderId(gatewayOrderId);
            if (depositOpt.isEmpty()) {
                log.warn("DepositRequest not found for gatewayOrderId [{}]. Skipping processing.", gatewayOrderId);
                return;
            }

            DepositRequest depositRequest = depositOpt.get();

            if ("payment.captured".equals(event)) {
                handlePaymentCaptured(depositRequest, gatewayPaymentId);
            } else if ("payment.failed".equals(event)) {
                handlePaymentFailed(depositRequest, gatewayPaymentId);
            } else {
                log.info("Unhandled Razorpay webhook event [{}] for order [{}]", event, gatewayOrderId);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse or process Razorpay webhook payload: {}", e.getMessage(), e);
            throw new RuntimeException("Webhook processing error: " + e.getMessage(), e);
        }
    }

    private void handlePaymentCaptured(DepositRequest depositRequest, String gatewayPaymentId) {
        // STEP 2: Idempotency Check at DepositRequest layer
        if (depositRequest.getStatus() == DepositStatus.COMPLETED) {
            log.info("IDEMPOTENT REPLAY: DepositRequest [{}] with order [{}] already COMPLETED. Skipping wallet credit.",
                    depositRequest.getId(), depositRequest.getGatewayOrderId());
            return;
        }

        String referenceId = "deposit:" + (gatewayPaymentId != null ? gatewayPaymentId : depositRequest.getGatewayOrderId());

        // STEP 3: Credit Wallet atomically & idempotently via Phase 6 WalletService
        walletService.applyLedgerEntry(
                depositRequest.getUserId(),
                LedgerEntryType.DEPOSIT,
                depositRequest.getAmountPaise(),
                referenceId
        );

        // STEP 4: Update DepositRequest record status
        depositRequest.setStatus(DepositStatus.COMPLETED);
        depositRequest.setGatewayPaymentId(gatewayPaymentId);
        depositRequest.setCompletedAt(Instant.now());
        depositRequestRepository.save(depositRequest);

        log.info("Successfully completed DepositRequest [{}] for user [{}], credited {} paise",
                depositRequest.getId(), depositRequest.getUserId(), depositRequest.getAmountPaise());

        // Phase 14: Retroactive notification call
        if (notificationService != null) {
            long rupees = depositRequest.getAmountPaise() / 100;
            notificationService.notify(
                    depositRequest.getUserId(),
                    com.teenpatti.platform.notification.NotificationType.DEPOSIT_SUCCESS,
                    "Your deposit of ₹" + rupees + " (" + depositRequest.getAmountPaise() + " paise) was successful."
            );
        }
    }

    private void handlePaymentFailed(DepositRequest depositRequest, String gatewayPaymentId) {
        if (depositRequest.getStatus() == DepositStatus.COMPLETED) {
            log.warn("Received payment.failed for already COMPLETED deposit request [{}]", depositRequest.getId());
            return;
        }

        depositRequest.setStatus(DepositStatus.FAILED);
        if (gatewayPaymentId != null) {
            depositRequest.setGatewayPaymentId(gatewayPaymentId);
        }
        depositRequest.setCompletedAt(Instant.now());
        depositRequestRepository.save(depositRequest);

        log.info("Marked DepositRequest [{}] as FAILED for order [{}]", depositRequest.getId(), depositRequest.getGatewayOrderId());
    }
}
