package com.peterscode.edutrack.payment.service;

import com.peterscode.edutrack.payment.dto.CreatePaymentInvoiceRequestDto;
import com.peterscode.edutrack.payment.dto.PaymentInvoiceDto;
import com.peterscode.edutrack.payment.dto.PaymentTransactionDto;

import java.util.List;

public interface PaymentInvoiceService {

    PaymentInvoiceDto createInvoice(Long schoolId, CreatePaymentInvoiceRequestDto requestDto);

    PaymentInvoiceDto getInvoice(String invoiceNumber);

    PaymentInvoiceDto getInvoiceForSchool(String invoiceNumber, Long schoolId);

    List<PaymentInvoiceDto> getSchoolInvoices(Long schoolId);

    void expireInvoices();

    void expireInvoicesScheduled();

    List<PaymentTransactionDto> getInvoiceTransactions(Long invoiceId);
}
