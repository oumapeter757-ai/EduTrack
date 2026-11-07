package com.peterscode.edutrack.payment.repository;

import com.peterscode.edutrack.payment.entity.PaymentInvoice;
import com.peterscode.edutrack.payment.enums.PaymentInvoiceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentInvoiceRepository extends JpaRepository<PaymentInvoice, Long> {

    Optional<PaymentInvoice> findByInvoiceNumber(String invoiceNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM PaymentInvoice i WHERE i.invoiceNumber = :invoiceNumber")
    Optional<PaymentInvoice> findByInvoiceNumberForUpdate(@Param("invoiceNumber") String invoiceNumber);

    Optional<PaymentInvoice> findByInvoiceNumberAndSchoolId(String invoiceNumber, Long schoolId);

    List<PaymentInvoice> findBySchoolIdAndStatus(Long schoolId, PaymentInvoiceStatus status);

    List<PaymentInvoice> findBySchoolId(Long schoolId);

    List<PaymentInvoice> findByStatusAndExpiresAtBefore(PaymentInvoiceStatus status, LocalDateTime now);

    /**
     * Claim a bounded batch of expired PENDING invoices using SKIP LOCKED.
     * Multiple instances calling this method will get disjoint sets of rows.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Transactional
    @Query("SELECT i FROM PaymentInvoice i WHERE i.status = :status " +
            "AND i.expiresAt < :now " +
            "AND (i.claimedBy IS NULL OR i.claimedUntil < :now) " +
            "ORDER BY i.expiresAt ASC")
    List<PaymentInvoice> claimExpiredInvoices(
            @Param("status") PaymentInvoiceStatus status,
            @Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE payment_invoices
            SET claimed_by = :runnerId, claimed_until = :leaseUntil
            WHERE id IN (
                SELECT id FROM (
                    SELECT id FROM payment_invoices
                    WHERE status = :status
                      AND expires_at < :now
                      AND (claimed_by IS NULL OR claimed_until < :now)
                    ORDER BY expires_at, id
                    LIMIT :batchSize
                ) candidates
            )
            """, nativeQuery = true)
    int claimExpiredInvoicesAtomically(@Param("status") String status,
                                       @Param("now") LocalDateTime now,
                                       @Param("runnerId") String runnerId,
                                       @Param("leaseUntil") LocalDateTime leaseUntil,
                                       @Param("batchSize") int batchSize);

    @Query("SELECT i FROM PaymentInvoice i WHERE i.claimedBy = :runnerId " +
            "AND i.claimedUntil = :leaseUntil ORDER BY i.expiresAt ASC")
    List<PaymentInvoice> findClaimedByRunner(@Param("runnerId") String runnerId,
                                             @Param("leaseUntil") LocalDateTime leaseUntil);

    /**
     * Return expired claims back to the queue.
     */
    @Modifying
    @Transactional
    @Query("UPDATE PaymentInvoice i SET i.claimedBy = NULL, i.claimedUntil = NULL " +
            "WHERE i.claimedUntil < :now AND i.status = :status")
    int returnExpiredClaims(
            @Param("now") LocalDateTime now,
            @Param("status") PaymentInvoiceStatus status);
}
