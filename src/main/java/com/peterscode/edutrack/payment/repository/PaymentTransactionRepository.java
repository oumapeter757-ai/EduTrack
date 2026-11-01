package com.peterscode.edutrack.payment.repository;

import com.peterscode.edutrack.payment.entity.PaymentTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    List<PaymentTransaction> findByInvoiceId(Long invoiceId);

    Page<PaymentTransaction> findAll(Pageable pageable);
}
