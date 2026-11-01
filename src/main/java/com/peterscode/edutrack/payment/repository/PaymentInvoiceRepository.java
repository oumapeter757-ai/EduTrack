package com.peterscode.edutrack.payment.repository;

import com.peterscode.edutrack.payment.entity.PaymentInvoice;
import com.peterscode.edutrack.payment.enums.PaymentInvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentInvoiceRepository extends JpaRepository<PaymentInvoice, Long> {

    Optional<PaymentInvoice> findByInvoiceNumber(String invoiceNumber);

    List<PaymentInvoice> findBySchoolIdAndStatus(Long schoolId, PaymentInvoiceStatus status);

    List<PaymentInvoice> findBySchoolId(Long schoolId);

    List<PaymentInvoice> findByStatusAndExpiresAtBefore(PaymentInvoiceStatus status, LocalDateTime now);
}
