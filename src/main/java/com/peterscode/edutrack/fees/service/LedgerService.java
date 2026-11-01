package com.peterscode.edutrack.fees.service;

import com.peterscode.edutrack.fees.entity.FeeInvoice;
import com.peterscode.edutrack.fees.entity.LedgerEntry;
import com.peterscode.edutrack.fees.entity.StudentFeeAccount;
import com.peterscode.edutrack.fees.enums.LedgerEntryType;
import com.peterscode.edutrack.fees.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

/**
 * Append-only ledger service. Every money movement creates a ledger entry.
 * <p>
 * Amount convention: positive for charges (money owed), negative for credits (money received).
 * </p>
 */
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final Clock clock;

    /**
     * Post a charge entry (positive amount = money owed).
     */
    public LedgerEntry postCharge(StudentFeeAccount account, FeeInvoice invoice,
                                  String description, BigDecimal amount) {
        LedgerEntry entry = LedgerEntry.builder()
                .schoolId(account.getSchoolId())
                .accountId(account.getId())
                .type(LedgerEntryType.CHARGE)
                .amount(amount)
                .invoiceId(invoice != null ? invoice.getId() : null)
                .description(description)
                .occurredAt(Instant.now(clock))
                .build();
        return ledgerEntryRepository.save(entry);
    }

    /**
     * Post a payment entry (negative amount = money received).
     */
    public LedgerEntry postPayment(StudentFeeAccount account, Long paymentId,
                                   Long invoiceId, String description, BigDecimal amount) {
        LedgerEntry entry = LedgerEntry.builder()
                .schoolId(account.getSchoolId())
                .accountId(account.getId())
                .type(LedgerEntryType.PAYMENT)
                .amount(amount.negate())
                .paymentId(paymentId)
                .invoiceId(invoiceId)
                .description(description)
                .occurredAt(Instant.now(clock))
                .build();
        return ledgerEntryRepository.save(entry);
    }

    /**
     * Post a discount entry (negative amount = credit).
     */
    public LedgerEntry postDiscount(StudentFeeAccount account, Long invoiceId,
                                    String description, BigDecimal amount, Long createdBy) {
        LedgerEntry entry = LedgerEntry.builder()
                .schoolId(account.getSchoolId())
                .accountId(account.getId())
                .type(LedgerEntryType.DISCOUNT)
                .amount(amount.negate())
                .invoiceId(invoiceId)
                .description(description)
                .createdBy(createdBy)
                .occurredAt(Instant.now(clock))
                .build();
        return ledgerEntryRepository.save(entry);
    }

    /**
     * Post a reversal entry that negates a previous entry.
     */
    public LedgerEntry postReversal(StudentFeeAccount account, Long originalEntryId,
                                    String reason, Long createdBy) {
        LedgerEntry entry = LedgerEntry.builder()
                .schoolId(account.getSchoolId())
                .accountId(account.getId())
                .type(LedgerEntryType.REVERSAL)
                .amount(BigDecimal.ZERO) // will be set by the caller
                .reversalOfEntryId(originalEntryId)
                .description("Reversal: " + reason)
                .createdBy(createdBy)
                .occurredAt(Instant.now(clock))
                .build();
        return ledgerEntryRepository.save(entry);
    }

    /**
     * Update the cached balance on the account after ledger entries are posted.
     */
    public void updateCachedBalance(StudentFeeAccount account) {
        BigDecimal sum = ledgerEntryRepository.sumAmountBySchoolIdAndAccountId(
                account.getSchoolId(), account.getId());
        if (sum != null) {
            account.setCachedBalance(sum);
        }
    }
}
