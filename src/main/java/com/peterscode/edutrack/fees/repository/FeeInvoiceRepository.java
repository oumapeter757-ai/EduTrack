package com.peterscode.edutrack.fees.repository;

import com.peterscode.edutrack.fees.entity.FeeInvoice;
import com.peterscode.edutrack.fees.enums.FeeInvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeeInvoiceRepository extends JpaRepository<FeeInvoice, Long> {

    Optional<FeeInvoice> findBySchoolIdAndId(Long schoolId, Long id);

    Optional<FeeInvoice> findBySchoolIdAndInvoiceNumber(Long schoolId, String invoiceNumber);

    Page<FeeInvoice> findBySchoolId(Long schoolId, Pageable pageable);

    Page<FeeInvoice> findBySchoolIdAndStudentId(Long schoolId, Long studentId, Pageable pageable);

    Page<FeeInvoice> findBySchoolIdAndStatus(Long schoolId, FeeInvoiceStatus status, Pageable pageable);

    List<FeeInvoice> findBySchoolIdAndStudentIdAndStatusInOrderByIssuedOnAsc(
            Long schoolId, Long studentId, List<FeeInvoiceStatus> statuses);

    boolean existsBySchoolIdAndStudentIdAndTermIdAndStructureId(
            Long schoolId, Long studentId, Long termId, Long structureId);
}
