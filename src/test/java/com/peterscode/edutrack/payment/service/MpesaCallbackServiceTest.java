package com.peterscode.edutrack.payment.service;

import com.peterscode.edutrack.payment.dto.MpesaCallbackRequest;
import com.peterscode.edutrack.payment.entity.PaymentInvoice;
import com.peterscode.edutrack.payment.enums.BillingPeriod;
import com.peterscode.edutrack.payment.enums.PaymentInvoiceStatus;
import com.peterscode.edutrack.payment.repository.PaymentInvoiceRepository;
import com.peterscode.edutrack.payment.repository.PaymentTransactionRepository;
import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import com.peterscode.edutrack.school.service.SchoolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MpesaCallbackServiceTest {

    @Mock
    private PaymentInvoiceRepository paymentInvoiceRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private SchoolService schoolService;

    @Mock
    private PaymentNotificationService paymentNotificationService;

    @InjectMocks
    private MpesaCallbackServiceImpl mpesaCallbackService;

    private PaymentInvoice pendingInvoice;
    private School testSchool;

    @BeforeEach
    void setUp() {
        testSchool = School.builder()
                .id(1L)
                .name("Test School")
                .code("TEST")
                .email("admin@testschool.edu")
                .subscriptionTier(SubscriptionTier.FREE)
                .build();

        pendingInvoice = PaymentInvoice.builder()
                .id(100L)
                .invoiceNumber("EDU-TEST-20250101120000")
                .school(testSchool)
                .tier(SubscriptionTier.STANDARD)
                .billingPeriod(BillingPeriod.TERM)
                .amount(new BigDecimal("5000.00"))
                .currency("KES")
                .status(PaymentInvoiceStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
    }

    private MpesaCallbackRequest buildCallback(String invoiceNumber, BigDecimal amount, String mpesaReceipt, int resultCode, String resultDesc) {
        MpesaCallbackRequest request = new MpesaCallbackRequest();
        MpesaCallbackRequest.Body body = new MpesaCallbackRequest.Body();
        MpesaCallbackRequest.StkCallback stk = new MpesaCallbackRequest.StkCallback();
        stk.setResultCode(resultCode);
        stk.setResultDesc(resultDesc != null ? resultDesc : "Success");
        stk.setCheckoutRequestID("REQ_12345");

        MpesaCallbackRequest.CallbackMetadata metadata = new MpesaCallbackRequest.CallbackMetadata();
        var items = new java.util.ArrayList<MpesaCallbackRequest.Item>();
        items.add(new MpesaCallbackRequest.Item("Account_Reference", invoiceNumber));
        if (amount != null) {
            items.add(new MpesaCallbackRequest.Item("Amount", amount.toString()));
        }
        if (mpesaReceipt != null) {
            items.add(new MpesaCallbackRequest.Item("MpesaReceiptNumber", mpesaReceipt));
        }
        metadata.setItem(items);
        stk.setCallbackMetadata(metadata);
        body.setStkCallback(stk);
        request.setBody(body);
        return request;
    }

    // ─── Validation Tests ───

    @Test
    @DisplayName("handleValidation accepts when invoice found and amount matches")
    void handleValidation_validInvoice_accepts() {
        when(paymentInvoiceRepository.findByInvoiceNumber(pendingInvoice.getInvoiceNumber()))
                .thenReturn(Optional.of(pendingInvoice));

        MpesaCallbackRequest callback = buildCallback(
                pendingInvoice.getInvoiceNumber(),
                pendingInvoice.getAmount(),
                null,
                0, "Success");

        MpesaCallbackRequest response = mpesaCallbackService.handleValidation(callback);

        assertThat(response.getBody().getStkCallback().getResultCode()).isEqualTo(0);
        assertThat(pendingInvoice.getStatus()).isEqualTo(PaymentInvoiceStatus.VALIDATED);
        assertThat(pendingInvoice.getValidatedAt()).isNotNull();
        verify(paymentInvoiceRepository).save(pendingInvoice);
        verify(paymentTransactionRepository).save(any());
    }

    @Test
    @DisplayName("handleValidation rejects when invoice not found")
    void handleValidation_invoiceNotFound_rejects() {
        when(paymentInvoiceRepository.findByInvoiceNumber(anyString()))
                .thenReturn(Optional.empty());

        MpesaCallbackRequest callback = buildCallback(
                "NONEXISTENT",
                new BigDecimal("5000"),
                null,
                0, "Success");

        MpesaCallbackRequest response = mpesaCallbackService.handleValidation(callback);

        assertThat(response.getBody().getStkCallback().getResultCode()).isEqualTo(1);
        verify(paymentInvoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleValidation rejects when amount mismatch")
    void handleValidation_amountMismatch_rejects() {
        when(paymentInvoiceRepository.findByInvoiceNumber(pendingInvoice.getInvoiceNumber()))
                .thenReturn(Optional.of(pendingInvoice));

        MpesaCallbackRequest callback = buildCallback(
                pendingInvoice.getInvoiceNumber(),
                new BigDecimal("1000.00"),
                null,
                0, "Success");

        MpesaCallbackRequest response = mpesaCallbackService.handleValidation(callback);

        assertThat(response.getBody().getStkCallback().getResultCode()).isEqualTo(1);
        assertThat(pendingInvoice.getStatus()).isEqualTo(PaymentInvoiceStatus.PENDING);
    }

    @Test
    @DisplayName("handleValidation rejects when ResultCode is non-zero")
    void handleValidation_failedResultCode_rejects() {
        MpesaCallbackRequest callback = buildCallback(
                pendingInvoice.getInvoiceNumber(),
                pendingInvoice.getAmount(),
                null,
                1, "Transaction failed");

        MpesaCallbackRequest response = mpesaCallbackService.handleValidation(callback);

        assertThat(response.getBody().getStkCallback().getResultCode()).isEqualTo(1);
        verify(paymentInvoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleValidation accepts when invoice already processed")
    void handleValidation_alreadyProcessed_accepts() {
        pendingInvoice.setStatus(PaymentInvoiceStatus.VALIDATED);
        when(paymentInvoiceRepository.findByInvoiceNumber(pendingInvoice.getInvoiceNumber()))
                .thenReturn(Optional.of(pendingInvoice));

        MpesaCallbackRequest callback = buildCallback(
                pendingInvoice.getInvoiceNumber(),
                pendingInvoice.getAmount(),
                null,
                0, "Success");

        MpesaCallbackRequest response = mpesaCallbackService.handleValidation(callback);

        assertThat(response.getBody().getStkCallback().getResultCode()).isEqualTo(0);
        verify(paymentInvoiceRepository, never()).save(any());
    }

    // ─── Confirmation Tests ───

    @Test
    @DisplayName("handleConfirmation confirms payment and triggers subscription activation")
    void handleConfirmation_validConfirmation_confirms() {
        when(paymentInvoiceRepository.findByInvoiceNumber(pendingInvoice.getInvoiceNumber()))
                .thenReturn(Optional.of(pendingInvoice));
        doNothing().when(schoolService).activateSubscriptionByPayment(anyLong());

        MpesaCallbackRequest callback = buildCallback(
                pendingInvoice.getInvoiceNumber(),
                pendingInvoice.getAmount(),
                "SLM1234567",
                0, "Success");

        mpesaCallbackService.handleConfirmation(callback);

        assertThat(pendingInvoice.getStatus()).isEqualTo(PaymentInvoiceStatus.CONFIRMED);
        assertThat(pendingInvoice.getMpesaTransactionId()).isEqualTo("SLM1234567");
        assertThat(pendingInvoice.getConfirmedAt()).isNotNull();
        verify(schoolService).activateSubscriptionByPayment(100L);
        verify(paymentNotificationService).sendPaymentConfirmationEmails(pendingInvoice);
        verify(paymentTransactionRepository, atLeast(2)).save(any());
    }

    @Test
    @DisplayName("handleConfirmation is idempotent - skips already confirmed invoice")
    void handleConfirmation_alreadyConfirmed_skips() {
        pendingInvoice.setStatus(PaymentInvoiceStatus.CONFIRMED);
        pendingInvoice.setMpesaTransactionId("SLM1234567");
        when(paymentInvoiceRepository.findByInvoiceNumber(pendingInvoice.getInvoiceNumber()))
                .thenReturn(Optional.of(pendingInvoice));

        MpesaCallbackRequest callback = buildCallback(
                pendingInvoice.getInvoiceNumber(),
                pendingInvoice.getAmount(),
                "SLM1234567",
                0, "Success");

        mpesaCallbackService.handleConfirmation(callback);

        verify(schoolService, never()).activateSubscriptionByPayment(anyLong());
        verify(paymentNotificationService, never()).sendPaymentConfirmationEmails(any());
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleConfirmation handles failed payment (ResultCode != 0)")
    void handleConfirmation_failedPayment_marksFailed() {
        when(paymentInvoiceRepository.findByInvoiceNumber(pendingInvoice.getInvoiceNumber()))
                .thenReturn(Optional.of(pendingInvoice));

        MpesaCallbackRequest callback = buildCallback(
                pendingInvoice.getInvoiceNumber(),
                pendingInvoice.getAmount(),
                null,
                1, "Payment declined");

        mpesaCallbackService.handleConfirmation(callback);

        assertThat(pendingInvoice.getStatus()).isEqualTo(PaymentInvoiceStatus.FAILED);
        verify(schoolService, never()).activateSubscriptionByPayment(anyLong());
        verify(paymentNotificationService, never()).sendPaymentConfirmationEmails(any());
    }

    @Test
    @DisplayName("handleConfirmation handles missing invoice gracefully")
    void handleConfirmation_invoiceNotFound_noError() {
        when(paymentInvoiceRepository.findByInvoiceNumber(anyString()))
                .thenReturn(Optional.empty());

        MpesaCallbackRequest callback = buildCallback(
                "NONEXISTENT",
                new BigDecimal("5000"),
                "SLM1234567",
                0, "Success");

        mpesaCallbackService.handleConfirmation(callback);

        verify(schoolService, never()).activateSubscriptionByPayment(anyLong());
        verify(paymentNotificationService, never()).sendPaymentConfirmationEmails(any());
    }

    @Test
    @DisplayName("handleConfirmation logs failure when subscription activation throws")
    void handleConfirmation_activationFailure_logsError() {
        when(paymentInvoiceRepository.findByInvoiceNumber(pendingInvoice.getInvoiceNumber()))
                .thenReturn(Optional.of(pendingInvoice));
        doThrow(new RuntimeException("DB connection failed"))
                .when(schoolService).activateSubscriptionByPayment(anyLong());

        MpesaCallbackRequest callback = buildCallback(
                pendingInvoice.getInvoiceNumber(),
                pendingInvoice.getAmount(),
                "SLM1234567",
                0, "Success");

        mpesaCallbackService.handleConfirmation(callback);

        assertThat(pendingInvoice.getStatus()).isEqualTo(PaymentInvoiceStatus.CONFIRMED);
        verify(paymentTransactionRepository, atLeast(1)).save(any());
        verify(paymentNotificationService, never()).sendPaymentConfirmationEmails(any());
    }
}
