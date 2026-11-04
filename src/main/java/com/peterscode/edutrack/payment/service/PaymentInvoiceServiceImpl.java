package com.peterscode.edutrack.payment.service;

import com.peterscode.edutrack.payment.config.MpesaConfig;
import com.peterscode.edutrack.payment.dto.CreatePaymentInvoiceRequestDto;
import com.peterscode.edutrack.payment.dto.PaymentInvoiceDto;
import com.peterscode.edutrack.payment.dto.PaymentTransactionDto;
import com.peterscode.edutrack.payment.entity.PaymentInvoice;
import com.peterscode.edutrack.payment.entity.PaymentTransaction;
import com.peterscode.edutrack.payment.dto.SubscriptionPriceDto;
import com.peterscode.edutrack.payment.enums.PaymentInvoiceStatus;
import com.peterscode.edutrack.payment.enums.PaymentTransactionEventType;
import com.peterscode.edutrack.payment.repository.PaymentInvoiceRepository;
import com.peterscode.edutrack.payment.repository.PaymentTransactionRepository;
import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Payment invoice service with multi-instance safe scheduled expiry.
 * <p>
 * The expireInvoicesScheduled method uses SKIP LOCKED so multiple instances
 * process disjoint sets of expired invoices.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentInvoiceServiceImpl implements PaymentInvoiceService {

    private final PaymentInvoiceRepository paymentInvoiceRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SubscriptionPriceService subscriptionPriceService;
    private final SchoolRepository schoolRepository;
    private final MpesaConfig mpesaConfig;

    @Value("${edutrack.payment-invoice.batch-size:100}")
    private int batchSize;

    @Value("${edutrack.payment-invoice.lease-minutes:10}")
    private int leaseMinutes;

    /**
     * Unique identifier for this job instance (set at startup).
     */
    private static final String RUNNER_ID = "invoice-expire-" + UUID.randomUUID().toString().substring(0, 8);

    @Override
    @Transactional
    public PaymentInvoiceDto createInvoice(Long schoolId, CreatePaymentInvoiceRequestDto requestDto) {
        expireInvoices();

        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found with id " + schoolId));

        SubscriptionPriceDto priceDto = subscriptionPriceService.getPrice(
                requestDto.getTier(), requestDto.getBillingPeriod());

        String invoiceNumber = generateInvoiceNumber(school.getCode());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusHours(24);

        PaymentInvoice invoice = PaymentInvoice.builder()
                .invoiceNumber(invoiceNumber)
                .school(school)
                .tier(requestDto.getTier())
                .billingPeriod(requestDto.getBillingPeriod())
                .amount(priceDto.getAmount())
                .currency(priceDto.getCurrency())
                .status(PaymentInvoiceStatus.PENDING)
                .expiresAt(expiresAt)
                .build();
        invoice = paymentInvoiceRepository.save(invoice);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .invoice(invoice)
                .eventType(PaymentTransactionEventType.INVOICE_CREATED)
                .amount(invoice.getAmount())
                .details("Invoice created for " + school.getName() + " - " + requestDto.getTier() + " " + requestDto.getBillingPeriod())
                .build();
        paymentTransactionRepository.save(transaction);

        log.info("Created invoice {} for school {} ({})", invoiceNumber, school.getName(), school.getCode());
        return toDto(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentInvoiceDto getInvoice(String invoiceNumber) {
        expireInvoices();
        return paymentInvoiceRepository.findByInvoiceNumber(invoiceNumber)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceNumber));
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentInvoiceDto getInvoiceForSchool(String invoiceNumber, Long schoolId) {
        return paymentInvoiceRepository.findByInvoiceNumberAndSchoolId(invoiceNumber, schoolId)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentInvoiceDto> getSchoolInvoices(Long schoolId) {
        return paymentInvoiceRepository.findBySchoolId(schoolId).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Expire all pending invoices that have passed their expiry date.
     * Used by createInvoice and getInvoice as a one-time check.
     * This method is NOT multi-instance safe — it processes all expired invoices.
     */
    @Override
    @Transactional
    public void expireInvoices() {
        LocalDateTime now = LocalDateTime.now();
        List<PaymentInvoice> expired = paymentInvoiceRepository.findByStatusAndExpiresAtBefore(
                PaymentInvoiceStatus.PENDING, now);
        for (PaymentInvoice invoice : expired) {
            invoice.setStatus(PaymentInvoiceStatus.EXPIRED);
            paymentInvoiceRepository.save(invoice);
            PaymentTransaction transaction = PaymentTransaction.builder()
                    .invoice(invoice)
                    .eventType(PaymentTransactionEventType.INVOICE_EXPIRED)
                    .amount(invoice.getAmount())
                    .details("Invoice " + invoice.getInvoiceNumber() + " expired after 24 hours")
                    .build();
            paymentTransactionRepository.save(transaction);
        }
        if (!expired.isEmpty()) {
            log.info("Expired {} pending invoices", expired.size());
        }
    }

    /**
     * Scheduled job that expires pending invoices using SKIP LOCKED.
     * Multiple instances process disjoint sets of expired invoices.
     */
    @Scheduled(fixedRate = 3600000)
    @Override
    public void expireInvoicesScheduled() {
        LocalDateTime now = LocalDateTime.now();

        // Return expired claims back to the queue
        int returned = returnExpiredClaims(now);
        if (returned > 0) {
            log.info("Invoice expiry: returned {} expired claims", returned);
        }

        // Claim a bounded batch of expired invoices with SKIP LOCKED
        List<PaymentInvoice> claimed;
        try {
            claimed = claimExpiredInvoices(PaymentInvoiceStatus.PENDING, now);
        } catch (Exception e) {
            log.error("Invoice expiry: failed to claim invoices", e);
            return;
        }

        if (claimed.isEmpty()) {
            return;
        }

        // Limit to batch size
        if (claimed.size() > batchSize) {
            claimed = claimed.subList(0, batchSize);
        }

        // Mark claimed rows with this runner's lease
        markClaimed(claimed, RUNNER_ID, now.plusMinutes(leaseMinutes));

        log.info("Invoice expiry: claimed {} invoices (runner={})", claimed.size(), RUNNER_ID);

        // Process each invoice in its own transaction
        for (PaymentInvoice invoice : claimed) {
            expireOneInvoice(invoice);
        }
    }

    /**
     * Expire a single invoice in its own transaction.
     */
    @Transactional
    public void expireOneInvoice(PaymentInvoice invoice) {
        // Reload to get latest version
        PaymentInvoice fresh = paymentInvoiceRepository.findById(invoice.getId())
                .orElse(null);
        if (fresh == null || fresh.getStatus() != PaymentInvoiceStatus.PENDING) {
            return;
        }
        fresh.setStatus(PaymentInvoiceStatus.EXPIRED);
        paymentInvoiceRepository.save(fresh);
        PaymentTransaction transaction = PaymentTransaction.builder()
                .invoice(fresh)
                .eventType(PaymentTransactionEventType.INVOICE_EXPIRED)
                .amount(fresh.getAmount())
                .details("Invoice " + fresh.getInvoiceNumber() + " expired after 24 hours")
                .build();
        paymentTransactionRepository.save(transaction);
    }

    /**
     * Return expired claims back to the queue.
     */
    @Transactional
    public int returnExpiredClaims(LocalDateTime now) {
        return paymentInvoiceRepository.returnExpiredClaims(now, PaymentInvoiceStatus.PENDING);
    }

    /**
     * Claim a bounded batch of expired invoices using SKIP LOCKED.
     */
    @Transactional
    public List<PaymentInvoice> claimExpiredInvoices(PaymentInvoiceStatus status, LocalDateTime now) {
        return paymentInvoiceRepository.claimExpiredInvoices(status, now);
    }

    /**
     * Mark claimed rows with this runner's lease.
     */
    @Transactional
    public void markClaimed(List<PaymentInvoice> invoices, String runnerId, LocalDateTime leaseUntil) {
        for (PaymentInvoice i : invoices) {
            i.setClaimedBy(runnerId);
            i.setClaimedUntil(leaseUntil);
            paymentInvoiceRepository.save(i);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTransactionDto> getInvoiceTransactions(Long invoiceId) {
        return paymentTransactionRepository.findByInvoiceId(invoiceId).stream()
                .map(this::toTransactionDto)
                .toList();
    }

    private String generateInvoiceNumber(String schoolCode) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "EDU-" + schoolCode + "-" + timestamp;
    }

    private PaymentInvoiceDto toDto(PaymentInvoice invoice) {
        return PaymentInvoiceDto.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .schoolId(invoice.getSchool().getId())
                .schoolName(invoice.getSchool().getName())
                .tier(invoice.getTier())
                .billingPeriod(invoice.getBillingPeriod())
                .amount(invoice.getAmount())
                .currency(invoice.getCurrency())
                .status(invoice.getStatus())
                .mpesaTransactionId(invoice.getMpesaTransactionId())
                .validatedAt(invoice.getValidatedAt())
                .confirmedAt(invoice.getConfirmedAt())
                .expiresAt(invoice.getExpiresAt())
                .createdAt(invoice.getCreatedAt())
                .build();
    }

    private PaymentTransactionDto toTransactionDto(PaymentTransaction transaction) {
        return PaymentTransactionDto.builder()
                .id(transaction.getId())
                .invoiceId(transaction.getInvoice().getId())
                .invoiceNumber(transaction.getInvoice().getInvoiceNumber())
                .eventType(transaction.getEventType())
                .mpesaTransactionId(transaction.getMpesaTransactionId())
                .amount(transaction.getAmount())
                .details(transaction.getDetails())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
