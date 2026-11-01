package com.peterscode.edutrack.fees.repository;

import com.peterscode.edutrack.fees.entity.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    Page<LedgerEntry> findBySchoolIdAndAccountId(Long schoolId, Long accountId, Pageable pageable);

    List<LedgerEntry> findBySchoolIdAndAccountIdAndInvoiceId(
            Long schoolId, Long accountId, Long invoiceId);

    List<LedgerEntry> findBySchoolIdAndAccountIdAndPaymentId(
            Long schoolId, Long accountId, Long paymentId);

    /**
     * Sum of all ledger entries for an account. Positive = owed, negative = paid.
     */
    BigDecimal sumAmountBySchoolIdAndAccountId(Long schoolId, Long accountId);

    /**
     * Sum of PAYMENT-type ledger entries by day for reconciliation.
     * Negative amounts represent money received.
     */
    @Query(value = """
            SELECT DATE(occurred_at) as day, SUM(amount) as total, COUNT(id) as cnt
            FROM ledger_entries
            WHERE school_id = :schoolId
              AND type = 'PAYMENT'
              AND DATE(occurred_at) BETWEEN :from AND :to
            GROUP BY DATE(occurred_at)
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> sumPaymentsByDay(
            @Param("schoolId") Long schoolId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
