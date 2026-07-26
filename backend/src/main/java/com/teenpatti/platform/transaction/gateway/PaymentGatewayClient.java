package com.teenpatti.platform.transaction.gateway;

/**
 * Abstraction interface for payment gateway SDK integrations (e.g. Razorpay, Cashfree).
 * Isolates payment gateway interactions from core deposit/withdrawal domain logic.
 */
public interface PaymentGatewayClient {

    /**
     * Initiates an order with the payment gateway.
     *
     * @param amountPaise Amount to charge in paise
     * @param receiptId Internal reference/receipt ID
     * @return GatewayOrder containing gateway order ID and parameters for checkout UI
     */
    GatewayOrder createOrder(long amountPaise, String receiptId);

    /**
     * Verifies the cryptographic signature of an incoming webhook payload.
     *
     * @param payload Raw HTTP request body payload string
     * @param signature Webhook signature header value (e.g., X-Razorpay-Signature)
     * @param webhookSecret Configured secret key for webhook signature validation
     * @return true if valid signature, false otherwise
     */
    boolean verifyWebhookSignature(String payload, String signature, String webhookSecret);
}
