package com.teenpatti.platform.transaction;

import com.teenpatti.platform.transaction.gateway.GatewayOrder;
import com.teenpatti.platform.transaction.gateway.RazorpayPaymentGatewayClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AmountConversionTest {

    @Test
    @DisplayName("Verify known amount in paise round-trips correctly through gateway order creation without unit mismatch")
    void amountConversion_PaiseToGateway_RoundTripsCorrectly() {
        RazorpayPaymentGatewayClient gatewayClient = new RazorpayPaymentGatewayClient(
                "rzp_test_dummyKeyId", "dummySecret"
        );

        long knownAmountPaise = 250_50L; // ₹250.50 = 25050 paise
        GatewayOrder order = gatewayClient.createOrder(knownAmountPaise, "rcpt_test_123");

        assertEquals(25050L, order.getAmountPaise(), "Amount in paise must match original value exactly");
        assertEquals("INR", order.getCurrency(), "Currency must be INR");
    }
}
