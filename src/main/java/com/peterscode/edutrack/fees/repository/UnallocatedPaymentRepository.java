package com.peterscode.edutrack.fees.repository;

import com.peterscode.edutrack.fees.entity.UnallocatedPayment;
import com.peterscode.edutrack.fees.enums.UnallocatedPaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UnallocatedPaymentRepository extends JpaRepository<UnallocatedPayment, Long> {

    Page<UnallocatedPayment> findBySchoolIdAndStatus(
            Long schoolId, UnallocatedPaymentStatus status, Pageable pageable);

    Page<UnallocatedPayment> findBySchoolId(
            Long schoolId, Pageable pageable);

    Optional<UnallocatedPayment> findBySchoolIdAndIdAndStatus(
            Long schoolId, Long id, UnallocatedPaymentStatus status);

    Optional<UnallocatedPayment> findBySchoolIdAndProviderReceiptAndChannel(
            Long schoolId, String providerReceipt, String channel);
}
