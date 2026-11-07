package com.peterscode.edutrack.fees.repository;

import com.peterscode.edutrack.fees.entity.FeePayment;
import com.peterscode.edutrack.fees.enums.FeePaymentChannel;
import com.peterscode.edutrack.fees.enums.FeePaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FeePaymentRepository extends JpaRepository<FeePayment, Long> {

    Optional<FeePayment> findBySchoolIdAndId(Long schoolId, Long id);

    Page<FeePayment> findBySchoolId(Long schoolId, Pageable pageable);

    Page<FeePayment> findBySchoolIdAndStudentId(Long schoolId, Long studentId, Pageable pageable);

    Page<FeePayment> findBySchoolIdAndStatus(Long schoolId, FeePaymentStatus status, Pageable pageable);

    List<FeePayment> findBySchoolIdAndStatusIn(Long schoolId, List<FeePaymentStatus> statuses);

    Optional<FeePayment> findBySchoolIdAndChannelAndProviderReceipt(
            Long schoolId, FeePaymentChannel channel, String providerReceipt);

    boolean existsBySchoolIdAndChannelAndProviderReceipt(
            Long schoolId, FeePaymentChannel channel, String providerReceipt);

    Optional<FeePayment> findBySchoolIdAndReceiptNumber(Long schoolId, String receiptNumber);

    Optional<FeePayment> findBySchoolIdAndCheckoutRequestID(Long schoolId, String checkoutRequestID);

    Optional<FeePayment> findBySchoolIdAndStudentIdAndStatus(
            Long schoolId, Long studentId, FeePaymentStatus status);

    List<FeePayment> findByStatusAndCreatedAtBefore(
            FeePaymentStatus status, LocalDateTime before);

    // ==================== Job Claiming (SKIP LOCKED) ====================

    /**
     * Claim a bounded batch of stale PENDING payments for the timeout job.
     * Uses PESSIMISTIC_WRITE with SKIP LOCKED so multiple instances claim disjoint rows.
     * Only claims unclaimed rows (claimed_by IS NULL or lease expired).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Transactional
    @Query("SELECT p FROM FeePayment p WHERE p.status = :status " +
            "AND p.createdAt < :before " +
            "AND (p.claimedBy IS NULL OR p.claimedUntil < :now) " +
            "ORDER BY p.createdAt ASC")
    List<FeePayment> claimStalePayments(
            @Param("status") FeePaymentStatus status,
            @Param("before") LocalDateTime before,
            @Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE fee_payments
            SET claimed_by = :runnerId, claimed_until = :leaseUntil
            WHERE id IN (
                SELECT id FROM (
                    SELECT id FROM fee_payments
                    WHERE status = :status
                      AND created_at < :before
                      AND (claimed_by IS NULL OR claimed_until < :now)
                    ORDER BY created_at, id
                    LIMIT :batchSize
                ) candidates
            )
            """, nativeQuery = true)
    int claimStalePaymentsAtomically(@Param("status") String status,
                                     @Param("before") LocalDateTime before,
                                     @Param("now") LocalDateTime now,
                                     @Param("runnerId") String runnerId,
                                     @Param("leaseUntil") LocalDateTime leaseUntil,
                                     @Param("batchSize") int batchSize);

    @Query("SELECT p FROM FeePayment p WHERE p.claimedBy = :runnerId " +
            "AND p.claimedUntil = :leaseUntil ORDER BY p.createdAt ASC")
    List<FeePayment> findClaimedByRunner(@Param("runnerId") String runnerId,
                                         @Param("leaseUntil") LocalDateTime leaseUntil);

    /**
     * Return expired claims back to the queue so another runner can pick them up.
     */
    @Modifying
    @Transactional
    @Query("UPDATE FeePayment p SET p.claimedBy = NULL, p.claimedUntil = NULL " +
            "WHERE p.claimedUntil < :now AND p.status = :status")
    int returnExpiredClaims(
            @Param("now") LocalDateTime now,
            @Param("status") FeePaymentStatus status);

    // ==================== Report Queries ====================

    /**
     * Sum of SUCCEEDED payments by day for a school.
     */
    @Query(value = """
            SELECT DATE(p.created_at) as day, SUM(p.amount) as total, COUNT(p.id) as cnt, p.currency
            FROM fee_payments p
            WHERE p.school_id = :schoolId
              AND p.status = 'SUCCEEDED'
              AND DATE(p.created_at) BETWEEN :from AND :to
            GROUP BY DATE(p.created_at), p.currency
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> sumSucceededByDay(
            @Param("schoolId") Long schoolId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Sum of SUCCEEDED payments by channel for a school.
     */
    @Query(value = """
            SELECT p.channel, SUM(p.amount) as total, COUNT(p.id) as cnt, p.currency
            FROM fee_payments p
            WHERE p.school_id = :schoolId
              AND p.status = 'SUCCEEDED'
              AND DATE(p.created_at) BETWEEN :from AND :to
            GROUP BY p.channel, p.currency
            ORDER BY channel
            """, nativeQuery = true)
    List<Object[]> sumSucceededByChannel(
            @Param("schoolId") Long schoolId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Count and sum of SUCCEEDED payments by day for reconciliation.
     */
    @Query(value = """
            SELECT DATE(p.created_at) as day, SUM(p.amount) as total, COUNT(p.id) as cnt, p.currency
            FROM fee_payments p
            WHERE p.school_id = :schoolId
              AND p.status = 'SUCCEEDED'
              AND DATE(p.created_at) BETWEEN :from AND :to
            GROUP BY DATE(p.created_at), p.currency
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> sumSucceededByDayForReconciliation(
            @Param("schoolId") Long schoolId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
