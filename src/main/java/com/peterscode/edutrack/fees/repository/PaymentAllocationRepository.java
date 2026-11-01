package com.peterscode.edutrack.fees.repository;

import com.peterscode.edutrack.fees.entity.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {

    List<PaymentAllocation> findByPaymentId(Long paymentId);

    List<PaymentAllocation> findByInvoiceId(Long invoiceId);

    void deleteByPaymentId(Long paymentId);

    void deleteByInvoiceId(Long invoiceId);
}
