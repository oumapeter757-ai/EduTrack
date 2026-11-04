package com.peterscode.edutrack.payment.service;

import com.peterscode.edutrack.payment.dto.MpesaCallbackRequest;
import com.peterscode.edutrack.payment.entity.PaymentInvoice;
import com.peterscode.edutrack.payment.entity.PaymentTransaction;
import com.peterscode.edutrack.payment.enums.PaymentInvoiceStatus;
import com.peterscode.edutrack.payment.enums.PaymentTransactionEventType;
import com.peterscode.edutrack.payment.repository.PaymentInvoiceRepository;
import com.peterscode.edutrack.payment.repository.PaymentTransactionRepository;
import com.peterscode.edutrack.school.service.SchoolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MpesaCallbackServiceImpl implements MpesaCallbackService {

    private final PaymentInvoiceRepository paymentInvoiceRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SchoolService schoolService;
    private final PaymentNotificationService paymentNotificationService;

    @Override
    @Transactional
    public MpesaCallbackRequest handleValidation(MpesaCallbackRequest callback) {
        MpesaCallbackRequest.StkCallback stk = callback.getBody().getStkCallback();
        log.info("Mpesa validation callback received: ResultCode={}, CheckoutRequestID={}",
                stk.getResultCode(), stk.getCheckoutRequestID());

        if (stk.getResultCode() != 0) {
            log.warn("Mpesa validation failed: {}", stk.getResultDesc());
            return buildRejectResponse(stk.getResultDesc());
        }

        String invoiceNumber = extractInvoiceNumber(callback);
        BigDecimal transAmount = extractAmount(callback);

        Optional<PaymentInvoice> invoiceOpt = paymentInvoiceRepository.findByInvoiceNumberForUpdate(invoiceNumber);
        if (invoiceOpt.isEmpty()) {
            log.warn("Validation: invoice not found for number {}", invoiceNumber);
            return buildRejectResponse("Invoice not found: " + invoiceNumber);
        }

        PaymentInvoice invoice = invoiceOpt.get();
        if (invoice.getStatus() != PaymentInvoiceStatus.PENDING) {
            log.info("Validation: invoice {} already processed with status {}", invoiceNumber, invoice.getStatus());
            return buildAcceptResponse();
        }

        if (transAmount == null || invoice.getAmount().compareTo(transAmount) != 0) {
            log.warn("Validation: amount mismatch for invoice {}. Expected: {}, Received: {}",
                    invoiceNumber, invoice.getAmount(), transAmount);
            return buildRejectResponse("Amount mismatch. Expected: " + invoice.getAmount());
        }

        invoice.setStatus(PaymentInvoiceStatus.VALIDATED);
        invoice.setValidatedAt(LocalDateTime.now());
        paymentInvoiceRepository.save(invoice);

        logTransaction(invoice, PaymentTransactionEventType.VALIDATION_RECEIVED,
                "Validation received for invoice " + invoiceNumber + " - Amount: " + transAmount);

        log.info("Validation accepted for invoice {}", invoiceNumber);
        return buildAcceptResponse();
    }

    @Override
    @Transactional
    public MpesaCallbackRequest handleConfirmation(MpesaCallbackRequest callback) {
        MpesaCallbackRequest.StkCallback stk = callback.getBody().getStkCallback();
        log.info("Mpesa confirmation callback received: ResultCode={}, CheckoutRequestID={}",
                stk.getResultCode(), stk.getCheckoutRequestID());

        if (stk.getResultCode() != 0) {
            log.warn("Mpesa confirmation failed: {}", stk.getResultDesc());
            handleFailedPayment(callback);
            return callback;
        }

        String invoiceNumber = extractInvoiceNumber(callback);
        String mpesaReceiptNumber = extractMpesaReceiptNumber(callback);
        BigDecimal transAmount = extractAmount(callback);

        Optional<PaymentInvoice> invoiceOpt = paymentInvoiceRepository.findByInvoiceNumberForUpdate(invoiceNumber);
        if (invoiceOpt.isEmpty()) {
            log.error("Confirmation: invoice not found for number {}", invoiceNumber);
            return callback;
        }

        PaymentInvoice invoice = invoiceOpt.get();

        // Idempotency check
        if (invoice.getStatus() == PaymentInvoiceStatus.CONFIRMED) {
            log.info("Confirmation: invoice {} already confirmed, skipping", invoiceNumber);
            return callback;
        }

        if ((invoice.getStatus() != PaymentInvoiceStatus.PENDING
                && invoice.getStatus() != PaymentInvoiceStatus.VALIDATED)
                || transAmount == null
                || invoice.getAmount().compareTo(transAmount) != 0
                || mpesaReceiptNumber == null
                || mpesaReceiptNumber.isBlank()
                || !invoice.getExpiresAt().isAfter(LocalDateTime.now())) {
            log.warn("Confirmation rejected for invoice {} due to invalid state or payment details", invoiceNumber);
            return buildRejectResponse("Payment confirmation could not be verified");
        }

        invoice.setStatus(PaymentInvoiceStatus.CONFIRMED);
        invoice.setConfirmedAt(LocalDateTime.now());
        invoice.setMpesaTransactionId(mpesaReceiptNumber);
        paymentInvoiceRepository.save(invoice);

        logTransaction(invoice, PaymentTransactionEventType.CONFIRMATION_RECEIVED,
                "Payment confirmed. Mpesa Receipt: " + mpesaReceiptNumber);

        // Activate subscription
        try {
            schoolService.activateSubscriptionByPayment(invoice.getId());
            logTransaction(invoice, PaymentTransactionEventType.SUBSCRIPTION_ACTIVATED,
                    "Subscription activated for school " + invoice.getSchool().getName());

            // Send dual email notifications (school admin + super admin)
            paymentNotificationService.sendPaymentConfirmationEmails(invoice);
        } catch (Exception e) {
            log.error("Failed to activate subscription for invoice {}: {}", invoiceNumber, e.getMessage(), e);
            logTransaction(invoice, PaymentTransactionEventType.FAILED,
                    "Subscription activation failed: " + e.getMessage());
        }

        log.info("Confirmation processed for invoice {}", invoiceNumber);
        return callback;
    }

    private void handleFailedPayment(MpesaCallbackRequest callback) {
        MpesaCallbackRequest.StkCallback stk = callback.getBody().getStkCallback();
        String invoiceNumber = extractInvoiceNumber(callback);
        Optional<PaymentInvoice> invoiceOpt = paymentInvoiceRepository.findByInvoiceNumberForUpdate(invoiceNumber);
        if (invoiceOpt.isPresent()) {
            PaymentInvoice invoice = invoiceOpt.get();
            if (invoice.getStatus() != PaymentInvoiceStatus.PENDING
                    && invoice.getStatus() != PaymentInvoiceStatus.VALIDATED) {
                return;
            }
            invoice.setStatus(PaymentInvoiceStatus.FAILED);
            paymentInvoiceRepository.save(invoice);
            logTransaction(invoice, PaymentTransactionEventType.FAILED,
                    "Payment failed: " + stk.getResultDesc());
        }
    }

    private String extractInvoiceNumber(MpesaCallbackRequest callback) {
        MpesaCallbackRequest.CallbackMetadata metadata = callback.getBody().getStkCallback().getCallbackMetadata();
        if (metadata != null && metadata.getItem() != null) {
            for (MpesaCallbackRequest.Item item : metadata.getItem()) {
                if ("Account_Reference".equals(item.getName())) {
                    return item.getValue();
                }
            }
        }
        return null;
    }

    private String extractMpesaReceiptNumber(MpesaCallbackRequest callback) {
        MpesaCallbackRequest.CallbackMetadata metadata = callback.getBody().getStkCallback().getCallbackMetadata();
        if (metadata != null && metadata.getItem() != null) {
            for (MpesaCallbackRequest.Item item : metadata.getItem()) {
                if ("MpesaReceiptNumber".equals(item.getName())) {
                    return item.getValue();
                }
            }
        }
        return null;
    }

    private BigDecimal extractAmount(MpesaCallbackRequest callback) {
        MpesaCallbackRequest.CallbackMetadata metadata = callback.getBody().getStkCallback().getCallbackMetadata();
        if (metadata != null && metadata.getItem() != null) {
            for (MpesaCallbackRequest.Item item : metadata.getItem()) {
                if ("Amount".equals(item.getName())) {
                    return new BigDecimal(item.getValue());
                }
            }
        }
        return null;
    }

    private void logTransaction(PaymentInvoice invoice, PaymentTransactionEventType eventType, String details) {
        PaymentTransaction transaction = PaymentTransaction.builder()
                .invoice(invoice)
                .eventType(eventType)
                .amount(invoice.getAmount())
                .mpesaTransactionId(invoice.getMpesaTransactionId())
                .details(details)
                .build();
        paymentTransactionRepository.save(transaction);
    }

    private MpesaCallbackRequest buildAcceptResponse() {
        MpesaCallbackRequest response = new MpesaCallbackRequest();
        MpesaCallbackRequest.Body body = new MpesaCallbackRequest.Body();
        MpesaCallbackRequest.StkCallback stk = new MpesaCallbackRequest.StkCallback();
        stk.setResultCode(0);
        stk.setResultDesc("Accept");
        body.setStkCallback(stk);
        response.setBody(body);
        return response;
    }

    private MpesaCallbackRequest buildRejectResponse(String reason) {
        MpesaCallbackRequest response = new MpesaCallbackRequest();
        MpesaCallbackRequest.Body body = new MpesaCallbackRequest.Body();
        MpesaCallbackRequest.StkCallback stk = new MpesaCallbackRequest.StkCallback();
        stk.setResultCode(1);
        stk.setResultDesc(reason);
        body.setStkCallback(stk);
        response.setBody(body);
        return response;
    }
}
