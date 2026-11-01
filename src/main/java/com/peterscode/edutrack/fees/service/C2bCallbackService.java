package com.peterscode.edutrack.fees.service;

import com.peterscode.edutrack.fees.entity.CallbackInbox;
import com.peterscode.edutrack.fees.entity.FeePayment;
import com.peterscode.edutrack.fees.entity.SchoolPaymentConfig;
import com.peterscode.edutrack.fees.entity.StudentFeeAccount;
import com.peterscode.edutrack.fees.entity.UnallocatedPayment;
import com.peterscode.edutrack.fees.enums.*;
import com.peterscode.edutrack.fees.repository.CallbackInboxRepository;
import com.peterscode.edutrack.fees.repository.FeePaymentRepository;
import com.peterscode.edutrack.fees.repository.StudentFeeAccountRepository;
import com.peterscode.edutrack.fees.repository.UnallocatedPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Processes C2B (Customer-to-Business) Mpesa callbacks.
 * <p>
 * Flow:
 * 1. Callback arrives → save to CallbackInbox (durable storage)
 * 2. Look up school by shortcode
 * 3. Match account reference to student by admission number
 * 4. If matched: create FeePayment and post to ledger
 * 5. If not matched: create UnallocatedPayment for manual resolution
 * 6. Mark inbox entry as processed
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class C2bCallbackService {

    private final CallbackInboxRepository inboxRepository;
    private final SchoolPaymentConfigService configService;
    private final PaymentMatchingService matchingService;
    private final PaymentPostingService paymentPostingService;
    private final UnallocatedPaymentRepository unallocatedRepo;
    private final FeePaymentRepository feePaymentRepository;
    private final StudentFeeAccountRepository studentFeeAccountRepository;
    private final LedgerService ledgerService;

    /**
     * Process a C2B confirmation callback.
     * Idempotent: processing the same provider receipt twice has no extra effect.
     */
    @Transactional
    public void processConfirmation(Long schoolId, CallbackChannel channel,
            String providerReceipt, BigDecimal amount, String accountReference,
            String phoneNumber, Instant receivedAt) {

        // 1. Save to durable inbox first
        CallbackInbox inbox = CallbackInbox.builder()
                .schoolId(schoolId)
                .channel(channel)
                .providerId(providerReceipt)
                .payerPhoneMasked(maskPhone(phoneNumber))
                .receivedAt(receivedAt)
                .minimalPayload(buildMinimalPayload(providerReceipt, amount, accountReference, phoneNumber))
                .build();
        inbox = inboxRepository.save(inbox);

        // 2. Check idempotency — has this payment already been processed?
        Optional<FeePayment> existing = feePaymentExists(schoolId, providerReceipt);
        if (existing.isPresent()) {
            inbox.setProcessed(true);
            inbox.setProcessingResult("Idempotent skip: payment already processed");
            inboxRepository.save(inbox);
            log.info("C2B callback idempotent skip: providerReceipt={}", providerReceipt);
            return;
        }

        // 3. Match account reference to student
        Optional<PaymentMatchingService.StudentMatch> match =
                matchingService.findStudentByAccountReference(schoolId, accountReference);

        if (match.isPresent()) {
            // 4. Matched — create and post payment
            processMatchedPayment(inbox, schoolId, match.get().studentId(), amount,
                    providerReceipt, accountReference, phoneNumber);
        } else {
            // 5. Not matched — queue as unallocated
            processUnallocatedPayment(inbox, schoolId, amount, providerReceipt,
                    accountReference, phoneNumber);
        }
    }

    /**
     * Process a C2B validation callback. Returns ACCEPT or REJECT.
     */
    @Transactional
    public String processValidation(Long schoolId, String providerReceipt,
            BigDecimal amount, String accountReference, String phoneNumber,
            Instant receivedAt) {

        // Save to inbox
        CallbackInbox inbox = CallbackInbox.builder()
                .schoolId(schoolId)
                .channel(CallbackChannel.C2B_VALIDATION)
                .providerId(providerReceipt)
                .payerPhoneMasked(maskPhone(phoneNumber))
                .receivedAt(receivedAt)
                .minimalPayload(buildMinimalPayload(providerReceipt, amount, accountReference, phoneNumber))
                .build();
        inbox = inboxRepository.save(inbox);

        // Check if config is enabled
        Optional<SchoolPaymentConfig> config = configService.getConfigBySchoolId(schoolId);
        if (config.isEmpty() || !Boolean.TRUE.equals(config.get().getEnabled())) {
            inbox.setProcessed(true);
            inbox.setProcessingResult("REJECT: config not enabled");
            inboxRepository.save(inbox);
            return "REJECT";
        }

        // Accept validation — confirmation will do the actual matching
        inbox.setProcessed(true);
        inbox.setProcessingResult("ACCEPT");
        inboxRepository.save(inbox);
        return "ACCEPT";
    }

    private void processMatchedPayment(CallbackInbox inbox, Long schoolId,
            Long studentId, BigDecimal amount, String providerReceipt,
            String accountReference, String phoneNumber) {
        try {
            // Ensure fee account exists
            StudentFeeAccount account = studentFeeAccountRepository
                    .findBySchoolIdAndStudentId(schoolId, studentId)
                    .orElseGet(() -> studentFeeAccountRepository.save(
                            StudentFeeAccount.builder()
                                    .schoolId(schoolId)
                                    .studentId(studentId)
                                    .build()));

            // Create a FeePayment record
            FeePayment payment = FeePayment.builder()
                    .schoolId(schoolId)
                    .studentId(studentId)
                    .channel(FeePaymentChannel.PAYBILL)
                    .amount(amount)
                    .currency(account.getCurrency())
                    .providerReceipt(providerReceipt)
                    .payerPhoneMasked(maskPhone(phoneNumber))
                    .accountReference(accountReference)
                    .status(FeePaymentStatus.SUCCEEDED)
                    .confirmedAt(Instant.now())
                    .build();

            payment = feePaymentRepository.save(payment);

            // Allocate to open invoices (oldest first)
            paymentPostingService.allocatePaymentToInvoices(account, payment);

            // Update cached balance
            ledgerService.updateCachedBalance(account);
            studentFeeAccountRepository.save(account);

            inbox.setProcessed(true);
            inbox.setProcessingResult("Payment posted to student " + studentId);
            inboxRepository.save(inbox);
            log.info("C2B payment matched and posted: providerReceipt={}, studentId={}",
                    providerReceipt, studentId);
        } catch (Exception e) {
            inbox.setProcessed(false);
            inbox.setProcessingResult("Error: " + e.getMessage());
            inbox.setRetryCount(inbox.getRetryCount() + 1);
            inboxRepository.save(inbox);
            log.error("C2B payment posting failed: providerReceipt={}", providerReceipt, e);
            throw e;
        }
    }

    private void processUnallocatedPayment(CallbackInbox inbox, Long schoolId,
            BigDecimal amount, String providerReceipt,
            String accountReference, String phoneNumber) {
        UnallocatedPayment unallocated = UnallocatedPayment.builder()
                .schoolId(schoolId)
                .amount(amount)
                .currency("KES")
                .channel(FeePaymentChannel.PAYBILL)
                .providerReceipt(providerReceipt)
                .accountReference(accountReference)
                .payerPhoneMasked(maskPhone(phoneNumber))
                .status(UnallocatedPaymentStatus.PENDING)
                .reason("No active student found with admission number: " +
                        PaymentMatchingService.normalizeAccountReference(accountReference))
                .build();
        unallocatedRepo.save(unallocated);

        inbox.setProcessed(true);
        inbox.setProcessingResult("Queued as unallocated payment");
        inboxRepository.save(inbox);
        log.info("C2B payment queued as unallocated: providerReceipt={}, accountRef={}",
                providerReceipt, accountReference);
    }

    private Optional<FeePayment> feePaymentExists(Long schoolId, String providerReceipt) {
        return feePaymentRepository.findBySchoolIdAndChannelAndProviderReceipt(
                schoolId, FeePaymentChannel.PAYBILL, providerReceipt);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() <= 4) return "****";
        return "****" + phone.substring(phone.length() - 4);
    }

    private String buildMinimalPayload(String receipt, BigDecimal amount,
            String accountRef, String phone) {
        // Store minimal payload without full phone number
        return String.format(
                "{\"providerReceipt\":\"%s\",\"amount\":%s,\"accountReference\":\"%s\",\"phoneMasked\":\"%s\"}",
                receipt, amount, accountRef, maskPhone(phone));
    }
}
