package com.teenpatti.platform.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenpatti.platform.auth.JwtTokenProvider;
import com.teenpatti.platform.transaction.dto.InitiateDepositRequest;
import com.teenpatti.platform.transaction.dto.InitiateWithdrawalRequest;
import com.teenpatti.platform.transaction.gateway.GatewayOrder;
import com.teenpatti.platform.transaction.gateway.PaymentGatewayClient;
import com.teenpatti.platform.user.AccountStatus;
import com.teenpatti.platform.user.KycStatus;
import com.teenpatti.platform.user.User;
import com.teenpatti.platform.user.UserRepository;
import com.teenpatti.platform.user.UserRole;
import com.teenpatti.platform.wallet.Wallet;
import com.teenpatti.platform.wallet.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private DepositRequestRepository depositRequestRepository;

    @Autowired
    private WithdrawalRequestRepository withdrawalRequestRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private PaymentGatewayClient paymentGatewayClient;

    private User unverifiedUser;
    private User verifiedUser;
    private String unverifiedToken;
    private String verifiedToken;

    @BeforeEach
    void setUp() {
        withdrawalRequestRepository.deleteAll();
        depositRequestRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();

        unverifiedUser = userRepository.save(User.builder()
                .email("unverified@example.com")
                .phoneNumber("9876543210")
                .passwordHash("hashed")
                .displayName("UnverifiedUser")
                .kycStatus(KycStatus.NOT_STARTED)
                .accountStatus(AccountStatus.ACTIVE)
                .role(UserRole.PLAYER)
                .build());

        walletRepository.save(Wallet.builder()
                .userId(unverifiedUser.getId())
                .balancePaise(100_000L) // ₹1,000.00
                .currency("INR")
                .build());

        unverifiedToken = jwtTokenProvider.generateAccessToken(unverifiedUser.getId());

        verifiedUser = userRepository.save(User.builder()
                .email("verified@example.com")
                .phoneNumber("9876543211")
                .passwordHash("hashed")
                .displayName("VerifiedUser")
                .kycStatus(KycStatus.VERIFIED)
                .accountStatus(AccountStatus.ACTIVE)
                .role(UserRole.PLAYER)
                .build());

        walletRepository.save(Wallet.builder()
                .userId(verifiedUser.getId())
                .balancePaise(200_000L) // ₹2,000.00
                .currency("INR")
                .build());

        verifiedToken = jwtTokenProvider.generateAccessToken(verifiedUser.getId());

        // Default mock gateway behavior
        Mockito.when(paymentGatewayClient.createOrder(anyLong(), anyString()))
                .thenAnswer(invocation -> {
                    long amount = invocation.getArgument(0);
                    return GatewayOrder.builder()
                            .orderId("order_test_123456")
                            .amountPaise(amount)
                            .currency("INR")
                            .keyId("rzp_test_dummyKeyId")
                            .build();
                });
    }

    @Test
    @DisplayName("Deposit initiation creates Razorpay order and PENDING DepositRequest")
    void initiateDeposit_Success() throws Exception {
        InitiateDepositRequest request = InitiateDepositRequest.builder()

                .amountPaise(50_000L) // ₹500
                .build();

        mockMvc.perform(post("/api/transactions/deposit/initiate")
                        .header("Authorization", "Bearer " + verifiedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.gatewayOrderId").value("order_test_123456"))
                .andExpect(jsonPath("$.data.amountPaise").value(50000));

        DepositRequest depositRequest = depositRequestRepository.findByGatewayOrderId("order_test_123456")
                .orElseThrow();
        assertEquals(verifiedUser.getId(), depositRequest.getUserId());
        assertEquals(DepositStatus.PENDING, depositRequest.getStatus());
        assertEquals(50_000L, depositRequest.getAmountPaise());
    }

    @Test
    @DisplayName("Deposit initiation with amount below minimum configured limit returns 400 Bad Request")
    void initiateDeposit_BelowMinLimit_Returns400() throws Exception {
        InitiateDepositRequest request = InitiateDepositRequest.builder()
                .amountPaise(500L) // ₹5.00 (min is ₹100 = 10,000 paise)
                .build();

        mockMvc.perform(post("/api/transactions/deposit/initiate")
                        .header("Authorization", "Bearer " + verifiedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TRANSACTION_AMOUNT"));
    }

    @Test
    @DisplayName("Valid webhook payment captured credits wallet exactly once and handles duplicate replays idempotently")
    void razorpayWebhook_PaymentCaptured_IdempotentCredit() throws Exception {
        // Create initial PENDING DepositRequest
        DepositRequest deposit = depositRequestRepository.save(DepositRequest.builder()
                .userId(verifiedUser.getId())
                .gatewayOrderId("order_captured_999")
                .amountPaise(100_000L) // ₹1,000
                .status(DepositStatus.PENDING)
                .build());

        String webhookPayload = """
                {
                  "event": "payment.captured",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_captured_111",
                        "order_id": "order_captured_999",
                        "amount": 100000,
                        "status": "captured"
                      }
                    }
                  }
                }
                """;

        Mockito.when(paymentGatewayClient.verifyWebhookSignature(eq(webhookPayload), eq("valid_sig"), anyString()))
                .thenReturn(true);

        // FIRST WEBHOOK CALL
        mockMvc.perform(post("/api/transactions/webhook/razorpay")
                        .header("X-Razorpay-Signature", "valid_sig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Check wallet balance updated: 200,000 + 100,000 = 300,000
        Wallet updatedWallet = walletRepository.findByUserId(verifiedUser.getId()).orElseThrow();
        assertEquals(300_000L, updatedWallet.getBalancePaise());

        DepositRequest completedDeposit = depositRequestRepository.findById(deposit.getId()).orElseThrow();
        assertEquals(DepositStatus.COMPLETED, completedDeposit.getStatus());
        assertEquals("pay_captured_111", completedDeposit.getGatewayPaymentId());

        // REPLAY SAME WEBHOOK CALL
        mockMvc.perform(post("/api/transactions/webhook/razorpay")
                        .header("X-Razorpay-Signature", "valid_sig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isOk());

        // Balance MUST REMAIN 300,000 (not 400,000)
        Wallet recheckedWallet = walletRepository.findByUserId(verifiedUser.getId()).orElseThrow();
        assertEquals(300_000L, recheckedWallet.getBalancePaise(), "Replayed webhook must not double-credit wallet");
    }

    @Test
    @DisplayName("Webhook with invalid signature returns 400 and does not touch wallet")
    void razorpayWebhook_InvalidSignature_Returns400() throws Exception {
        String webhookPayload = """
                {
                  "event": "payment.captured",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_bad_999",
                        "order_id": "order_test_123456",
                        "amount": 100000
                      }
                    }
                  }
                }
                """;

        Mockito.when(paymentGatewayClient.verifyWebhookSignature(eq(webhookPayload), eq("invalid_sig"), anyString()))
                .thenReturn(false);

        mockMvc.perform(post("/api/transactions/webhook/razorpay")
                        .header("X-Razorpay-Signature", "invalid_sig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_WEBHOOK_SIGNATURE"));

        // Check balance unchanged
        Wallet wallet = walletRepository.findByUserId(verifiedUser.getId()).orElseThrow();
        assertEquals(200_000L, wallet.getBalancePaise());
    }

    @Test
    @DisplayName("Webhook payment failed marks DepositRequest FAILED without crediting wallet")
    void razorpayWebhook_PaymentFailed_MarksFailed() throws Exception {
        DepositRequest deposit = depositRequestRepository.save(DepositRequest.builder()
                .userId(verifiedUser.getId())
                .gatewayOrderId("order_failed_888")
                .amountPaise(50_000L)
                .status(DepositStatus.PENDING)
                .build());

        String webhookPayload = """
                {
                  "event": "payment.failed",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_failed_000",
                        "order_id": "order_failed_888",
                        "amount": 50000,
                        "status": "failed"
                      }
                    }
                  }
                }
                """;

        Mockito.when(paymentGatewayClient.verifyWebhookSignature(eq(webhookPayload), eq("valid_sig"), anyString()))
                .thenReturn(true);

        mockMvc.perform(post("/api/transactions/webhook/razorpay")
                        .header("X-Razorpay-Signature", "valid_sig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isOk());

        DepositRequest failedDeposit = depositRequestRepository.findById(deposit.getId()).orElseThrow();
        assertEquals(DepositStatus.FAILED, failedDeposit.getStatus());

        // Balance unchanged
        Wallet wallet = walletRepository.findByUserId(verifiedUser.getId()).orElseThrow();
        assertEquals(200_000L, wallet.getBalancePaise());
    }

    @Test
    @DisplayName("Withdrawal request from non-VERIFIED KYC user returns 403 Forbidden")
    void requestWithdrawal_UnverifiedKyc_Returns403() throws Exception {
        InitiateWithdrawalRequest request = InitiateWithdrawalRequest.builder()
                .amountPaise(50_000L) // ₹500
                .accountNumber("123456789012")
                .ifscCode("HDFC0001234")
                .accountHolderName("John Doe")
                .build();

        mockMvc.perform(post("/api/transactions/withdraw/request")
                        .header("Authorization", "Bearer " + unverifiedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("KYC_REQUIRED"));
    }

    @Test
    @DisplayName("Withdrawal request from VERIFIED KYC user debits wallet immediately and creates PENDING_ADMIN_REVIEW record")
    void requestWithdrawal_VerifiedKyc_Success() throws Exception {
        InitiateWithdrawalRequest request = InitiateWithdrawalRequest.builder()
                .amountPaise(50_000L) // ₹500
                .accountNumber("123456789012")
                .ifscCode("HDFC0001234")
                .accountHolderName("Jane Doe")
                .build();

        mockMvc.perform(post("/api/transactions/withdraw/request")
                        .header("Authorization", "Bearer " + verifiedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING_ADMIN_REVIEW"))
                .andExpect(jsonPath("$.data.amountPaise").value(50000));

        // Verify wallet debited immediately: 200,000 - 50,000 = 150,000
        Wallet wallet = walletRepository.findByUserId(verifiedUser.getId()).orElseThrow();
        assertEquals(150_000L, wallet.getBalancePaise());

        WithdrawalRequest withdrawalRequest = withdrawalRequestRepository.findByUserId(verifiedUser.getId(), null)
                .getContent().get(0);
        assertEquals(WithdrawalStatus.PENDING_ADMIN_REVIEW, withdrawalRequest.getStatus());
        assertEquals("123456789012", withdrawalRequest.getAccountNumber());
    }

    @Test
    @DisplayName("Withdrawal request exceeding available balance returns 402 Payment Required")
    void requestWithdrawal_ExceedingBalance_Returns402() throws Exception {
        InitiateWithdrawalRequest request = InitiateWithdrawalRequest.builder()
                .amountPaise(500_000L) // ₹5,000 (balance is ₹2,000)
                .accountNumber("123456789012")
                .ifscCode("HDFC0001234")
                .accountHolderName("Jane Doe")
                .build();

        mockMvc.perform(post("/api/transactions/withdraw/request")
                        .header("Authorization", "Bearer " + verifiedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FUNDS"));

        // Verify balance unchanged
        Wallet wallet = walletRepository.findByUserId(verifiedUser.getId()).orElseThrow();
        assertEquals(200_000L, wallet.getBalancePaise());
    }

    @Test
    @DisplayName("GET /api/transactions/deposit/{id} returns deposit status for owner")
    void getDepositStatus_Success() throws Exception {
        DepositRequest deposit = depositRequestRepository.save(DepositRequest.builder()
                .userId(verifiedUser.getId())
                .gatewayOrderId("order_get_123")
                .amountPaise(50_000L)
                .status(DepositStatus.PENDING)
                .build());

        mockMvc.perform(get("/api/transactions/deposit/" + deposit.getId())
                        .header("Authorization", "Bearer " + verifiedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(deposit.getId()))
                .andExpect(jsonPath("$.data.gatewayOrderId").value("order_get_123"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/transactions/withdraw/{id} returns withdrawal status for owner")
    void getWithdrawalStatus_Success() throws Exception {
        WithdrawalRequest withdrawal = withdrawalRequestRepository.save(WithdrawalRequest.builder()
                .userId(verifiedUser.getId())
                .amountPaise(50_000L)
                .accountNumber("123456789012")
                .ifscCode("HDFC0001234")
                .accountHolderName("Jane Doe")
                .status(WithdrawalStatus.PENDING_ADMIN_REVIEW)
                .build());

        mockMvc.perform(get("/api/transactions/withdraw/" + withdrawal.getId())
                        .header("Authorization", "Bearer " + verifiedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(withdrawal.getId()))
                .andExpect(jsonPath("$.data.status").value("PENDING_ADMIN_REVIEW"));
    }
}
