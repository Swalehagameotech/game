package com.teenpatti.platform.transaction.gateway;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Concrete Razorpay payment gateway integration using Razorpay Java SDK.
 * Manages order creation and signature verification.
 */
@Slf4j
@Component
public class RazorpayPaymentGatewayClient implements PaymentGatewayClient {

    private final String keyId;
    private final String keySecret;

    public RazorpayPaymentGatewayClient(
            @Value("${razorpay.key-id:rzp_test_dummyKeyId}") String keyId,
            @Value("${razorpay.key-secret:dummySecret}") String keySecret) {
        this.keyId = keyId;
        this.keySecret = keySecret;
    }

    @Override
    public GatewayOrder createOrder(long amountPaise, String receiptId) {
        // If dummy key is used (local dev or unit test), return mock GatewayOrder
        if (keyId.startsWith("rzp_test_dummy") || keySecret.equals("dummySecret")) {
            log.info("Using test/mock Razorpay credentials for order creation. Receipt ID: {}", receiptId);
            String dummyOrderId = "order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
            return GatewayOrder.builder()
                    .orderId(dummyOrderId)
                    .amountPaise(amountPaise)
                    .currency("INR")
                    .keyId(keyId)
                    .build();
        }

        try {
            RazorpayClient razorpayClient = new RazorpayClient(keyId, keySecret);
            JSONObject orderRequest = new JSONObject();
            // Razorpay API expects amount in paise (smallest currency unit for INR)
            orderRequest.put("amount", amountPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", receiptId);

            Order order = razorpayClient.orders.create(orderRequest);
            String gatewayOrderId = order.get("id");

            log.info("Successfully created Razorpay order [{}] for amount {} paise", gatewayOrderId, amountPaise);
            return GatewayOrder.builder()
                    .orderId(gatewayOrderId)
                    .amountPaise(amountPaise)
                    .currency("INR")
                    .keyId(keyId)
                    .build();
        } catch (RazorpayException e) {
            log.error("Failed to create Razorpay order for receipt [{}]: {}", receiptId, e.getMessage(), e);
            throw new RuntimeException("Payment gateway order creation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature, String webhookSecret) {
        if (signature == null || signature.isBlank()) {
            log.warn("Webhook signature missing or blank");
            return false;
        }

        try {
            return Utils.verifyWebhookSignature(payload, signature, webhookSecret);
        } catch (RazorpayException e) {
            log.warn("Razorpay webhook signature verification failed: {}", e.getMessage());
            return false;
        }
    }
}
