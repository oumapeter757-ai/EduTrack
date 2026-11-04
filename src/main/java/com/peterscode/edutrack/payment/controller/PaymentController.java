package com.peterscode.edutrack.payment.controller;

import com.peterscode.edutrack.payment.dto.CreatePaymentInvoiceRequestDto;
import com.peterscode.edutrack.payment.dto.PaymentInvoiceDto;
import com.peterscode.edutrack.payment.dto.PaymentTransactionDto;
import com.peterscode.edutrack.payment.enums.PaymentInvoiceStatus;
import com.peterscode.edutrack.payment.repository.PaymentInvoiceRepository;
import com.peterscode.edutrack.payment.repository.PaymentTransactionRepository;
import com.peterscode.edutrack.payment.service.PaymentInvoiceService;
import com.peterscode.edutrack.security.NoModuleRequired;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

@RestController
@NoModuleRequired
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentInvoiceService paymentInvoiceService;
    private final PaymentInvoiceRepository paymentInvoiceRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    @PostMapping("/invoice")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<PaymentInvoiceDto>> createInvoice(
            @Valid @RequestBody CreatePaymentInvoiceRequestDto requestDto,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long schoolId = principal.getSchoolId();
        if (schoolId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("User has no associated school"));
        }
        PaymentInvoiceDto invoice = paymentInvoiceService.createInvoice(schoolId, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment invoice created", invoice));
    }

    @GetMapping("/invoice/{invoiceNumber}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<PaymentInvoiceDto>> getInvoice(
            @PathVariable String invoiceNumber,
            @AuthenticationPrincipal UserPrincipal principal) {
        PaymentInvoiceDto invoice = principal.getRole() == Role.SUPER_ADMIN
                ? paymentInvoiceService.getInvoice(invoiceNumber)
                : paymentInvoiceService.getInvoiceForSchool(invoiceNumber, principal.getSchoolId());
        return ResponseEntity.ok(ApiResponse.success("Invoice retrieved", invoice));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<List<PaymentInvoiceDto>>> getPaymentHistory(
            @AuthenticationPrincipal UserPrincipal principal) {
        Long schoolId = principal.getSchoolId();
        if (schoolId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("User has no associated school"));
        }
        List<PaymentInvoiceDto> invoices = paymentInvoiceService.getSchoolInvoices(schoolId);
        return ResponseEntity.ok(ApiResponse.success("Payment history retrieved", invoices));
    }

    @GetMapping("/admin/transactions")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<PaymentTransactionDto>>> getAllTransactions(Pageable pageable) {
        Page<com.peterscode.edutrack.payment.entity.PaymentTransaction> page = paymentTransactionRepository.findAll(pageable);
        Page<PaymentTransactionDto> dtoPage = page.map(t -> PaymentTransactionDto.builder()
                .id(t.getId())
                .invoiceId(t.getInvoice().getId())
                .invoiceNumber(t.getInvoice().getInvoiceNumber())
                .eventType(t.getEventType())
                .mpesaTransactionId(t.getMpesaTransactionId())
                .amount(t.getAmount())
                .details(t.getDetails())
                .createdAt(t.getCreatedAt())
                .build());
        return ResponseEntity.ok(ApiResponse.success("All transactions retrieved", dtoPage));
    }

    @GetMapping("/admin/invoices")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<PaymentInvoiceDto>>> getAllInvoices(
            @RequestParam(required = false) PaymentInvoiceStatus status) {
        List<PaymentInvoiceDto> invoices;
        if (status != null) {
            invoices = paymentInvoiceRepository.findAll().stream()
                    .filter(i -> i.getStatus() == status)
                    .map(i -> PaymentInvoiceDto.builder()
                            .id(i.getId())
                            .invoiceNumber(i.getInvoiceNumber())
                            .schoolId(i.getSchool().getId())
                            .schoolName(i.getSchool().getName())
                            .tier(i.getTier())
                            .billingPeriod(i.getBillingPeriod())
                            .amount(i.getAmount())
                            .currency(i.getCurrency())
                            .status(i.getStatus())
                            .mpesaTransactionId(i.getMpesaTransactionId())
                            .validatedAt(i.getValidatedAt())
                            .confirmedAt(i.getConfirmedAt())
                            .expiresAt(i.getExpiresAt())
                            .createdAt(i.getCreatedAt())
                            .build())
                    .toList();
        } else {
            invoices = paymentInvoiceRepository.findAll().stream()
                    .map(i -> PaymentInvoiceDto.builder()
                            .id(i.getId())
                            .invoiceNumber(i.getInvoiceNumber())
                            .schoolId(i.getSchool().getId())
                            .schoolName(i.getSchool().getName())
                            .tier(i.getTier())
                            .billingPeriod(i.getBillingPeriod())
                            .amount(i.getAmount())
                            .currency(i.getCurrency())
                            .status(i.getStatus())
                            .mpesaTransactionId(i.getMpesaTransactionId())
                            .validatedAt(i.getValidatedAt())
                            .confirmedAt(i.getConfirmedAt())
                            .expiresAt(i.getExpiresAt())
                            .createdAt(i.getCreatedAt())
                            .build())
                    .toList();
        }
        return ResponseEntity.ok(ApiResponse.success("All invoices retrieved", invoices));
    }
}
