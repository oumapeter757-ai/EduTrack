package com.peterscode.edutrack.fees.controller;

import com.peterscode.edutrack.fees.dto.*;
import com.peterscode.edutrack.fees.entity.SchoolPaymentConfig;
import com.peterscode.edutrack.fees.service.*;
import com.peterscode.edutrack.fees.enums.ShortcodeType;
import com.peterscode.edutrack.fees.enums.MpesaEnvironment;
import com.peterscode.edutrack.fees.service.CsvExporter;
import com.peterscode.edutrack.guardian.repository.GuardianRepository;
import com.peterscode.edutrack.guardian.repository.ParentStudentMappingRepository;
import com.peterscode.edutrack.security.NoModuleRequired;
import com.peterscode.edutrack.security.ParentAccessGuard;
import com.peterscode.edutrack.security.RequiresModule;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.shared.response.ApiResponse;
import com.peterscode.edutrack.shared.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fees module controller.
 * <p>
 * All user-facing endpoints require the BILLING module (PREMIUM tier).
 * Callback endpoints are public and marked @NoModuleRequired.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/fees")
@RequiredArgsConstructor
@RequiresModule(ServiceModule.BILLING)
public class FeesController {

    private final FeeStructureService feeStructureService;
    private final FeeInvoiceService feeInvoiceService;
    private final PaymentPostingService paymentPostingService;
    private final FeeStatementService feeStatementService;
    private final SchoolPaymentConfigService paymentConfigService;
    private final UnallocatedPaymentService unallocatedPaymentService;
    private final StkPushService stkPushService;
    private final FeeReportService feeReportService;
    private final GuardianRepository guardianRepository;
    private final ParentStudentMappingRepository parentStudentMappingRepository;

    // ==================== Fee Structures ====================

    @PostMapping("/structures")
    public ApiResponse<FeeStructureView> createFeeStructure(
            @Valid @RequestBody CreateFeeStructureRequestDto request,
            @AuthenticationPrincipal UserPrincipal principal) {
        FeeStructureView view = feeStructureService.create(principal, request);
        return ApiResponse.success("Fee structure created", view);
    }

    @GetMapping("/structures")
    public ApiResponse<PageResponse<FeeStructureView>> listFeeStructures(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(
                "Fee structures retrieved",
                PageResponse.from(feeStructureService.list(principal, pageable)));
    }

    @GetMapping("/structures/{id}")
    public ApiResponse<FeeStructureView> getFeeStructure(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                "Fee structure retrieved",
                feeStructureService.getById(principal, id));
    }

    @PatchMapping("/structures/{id}/activate")
    public ApiResponse<Void> activateFeeStructure(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        feeStructureService.activate(principal, id);
        return ApiResponse.success("Fee structure activated", null);
    }

    // ==================== Invoices ====================

    @PostMapping("/structures/{structureId}/generate-invoices")
    public ApiResponse<Integer> generateInvoices(
            @PathVariable Long structureId,
            @AuthenticationPrincipal UserPrincipal principal) {
        int count = feeInvoiceService.generateInvoices(principal, structureId);
        return ApiResponse.success(count + " invoices generated", count);
    }

    @GetMapping("/invoices")
    public ApiResponse<PageResponse<FeeInvoiceView>> listInvoices(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(
                "Invoices retrieved",
                PageResponse.from(feeInvoiceService.listAll(principal, pageable)));
    }

    @GetMapping("/invoices/{id}")
    public ApiResponse<FeeInvoiceView> getInvoice(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                "Invoice retrieved",
                feeInvoiceService.getById(principal, id));
    }

    @GetMapping("/students/{studentId}/invoices")
    public ApiResponse<PageResponse<FeeInvoiceView>> getStudentInvoices(
            @PathVariable Long studentId,
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(
                "Student invoices retrieved",
                PageResponse.from(feeInvoiceService.listByStudent(principal, studentId, pageable)));
    }

    // ==================== Payments ====================

    @PostMapping("/payments/manual")
    public ApiResponse<FeePaymentView> recordManualPayment(
            @Valid @RequestBody ManualPaymentRequestDto request,
            @AuthenticationPrincipal UserPrincipal principal) {
        FeePaymentView view = paymentPostingService.recordManualPayment(principal, request);
        return ApiResponse.success("Payment recorded", view);
    }

    // ==================== STK Push ====================

    @PostMapping("/payments/stk")
    public ApiResponse<StkPushResponseDto> initiateStkPayment(
            @Valid @RequestBody StkPushRequestDto request,
            @AuthenticationPrincipal UserPrincipal principal) {
        StkPushResponseDto response = stkPushService.initiate(principal, request);
        return ApiResponse.success("STK Push initiated", response);
    }

    @GetMapping("/payments/{id}/status")
    public ApiResponse<FeePaymentView> getPaymentStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        com.peterscode.edutrack.fees.entity.FeePayment payment =
                stkPushService.getPaymentStatus(principal, id);
        FeePaymentView view = new FeePaymentView(
                payment.getId(), payment.getStudentId(), "Student",
                payment.getChannel(), payment.getAmount(), payment.getCurrency(),
                payment.getPayerPhoneMasked(), payment.getStatus(),
                payment.getReceiptNumber(), payment.getConfirmedAt(), payment.getCreatedAt());
        return ApiResponse.success("Payment status retrieved", view);
    }

    @GetMapping("/payments")
    public ApiResponse<PageResponse<FeePaymentView>> listPayments(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(
                "Payments retrieved",
                PageResponse.from(paymentPostingService.listAll(principal, pageable)));
    }

    @GetMapping("/students/{studentId}/payments")
    public ApiResponse<PageResponse<FeePaymentView>> getStudentPayments(
            @PathVariable Long studentId,
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(
                "Student payments retrieved",
                PageResponse.from(paymentPostingService.listByStudent(principal, studentId, pageable)));
    }

    // ==================== Statements ====================

    @GetMapping("/students/{studentId}/statement")
    public ApiResponse<FeeStatementView> getStatement(
            @PathVariable Long studentId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                "Statement retrieved",
                feeStatementService.getStatement(principal, studentId, page, size));
    }

    // ==================== Parent Views ====================

    @GetMapping("/parent/children")
    public ApiResponse<List<LinkedChildView>> listLinkedChildren(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<LinkedChildView> children = feeStatementService.listLinkedChildren(
                principal, guardianRepository, parentStudentMappingRepository);
        return ApiResponse.success("Linked children retrieved", children);
    }

    @GetMapping("/parent/{studentId}/invoices")
    public ApiResponse<PageResponse<FeeInvoiceView>> getParentInvoices(
            @PathVariable Long studentId,
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        ParentAccessGuard.assertLinked(
                principal.getSchoolId(), principal,
                parentStudentMappingRepository, guardianRepository, studentId);
        return ApiResponse.success(
                "Invoices retrieved",
                PageResponse.from(feeInvoiceService.listByStudent(principal, studentId, pageable)));
    }

    @GetMapping("/parent/{studentId}/payments")
    public ApiResponse<PageResponse<FeePaymentView>> getParentPayments(
            @PathVariable Long studentId,
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        ParentAccessGuard.assertLinked(
                principal.getSchoolId(), principal,
                parentStudentMappingRepository, guardianRepository, studentId);
        return ApiResponse.success(
                "Payments retrieved",
                PageResponse.from(paymentPostingService.listByStudent(principal, studentId, pageable)));
    }

    @GetMapping("/parent/{studentId}/statement")
    public ApiResponse<FeeStatementView> getParentStatement(
            @PathVariable Long studentId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ParentAccessGuard.assertLinked(
                principal.getSchoolId(), principal,
                parentStudentMappingRepository, guardianRepository, studentId);
        return ApiResponse.success(
                "Statement retrieved",
                feeStatementService.getStatement(principal, studentId, page, size));
    }

    // ==================== Student Self-Service Views ====================

    @GetMapping("/student/invoices")
    public ApiResponse<PageResponse<FeeInvoiceView>> getStudentInvoices(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(
                "Invoices retrieved",
                PageResponse.from(feeInvoiceService.listByStudent(principal, null, pageable)));
    }

    @GetMapping("/student/payments")
    public ApiResponse<PageResponse<FeePaymentView>> getStudentPayments(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(
                "Payments retrieved",
                PageResponse.from(paymentPostingService.listByStudent(principal, null, pageable)));
    }

    @GetMapping("/student/statement")
    public ApiResponse<FeeStatementView> getStudentStatement(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                "Statement retrieved",
                feeStatementService.getStatement(principal, null, page, size));
    }

    // ==================== Payment Config (SCHOOL_ADMIN only) ====================

    @GetMapping("/config")
    public ApiResponse<PaymentConfigView> getPaymentConfig(
            @AuthenticationPrincipal UserPrincipal principal) {
        Optional<SchoolPaymentConfig> config = paymentConfigService.getConfig(principal);
        if (config.isEmpty()) {
            return ApiResponse.success("No payment config", null);
        }
        SchoolPaymentConfig c = config.get();
        PaymentConfigView view = new PaymentConfigView(
                c.getId(), c.getSchoolId(), c.getShortcode(), c.getShortcodeType(),
                c.getEnvironment().name(), c.getEnabled(),
                c.getEncryptedConsumerKey() != null, c.getCreatedAt());
        return ApiResponse.success("Payment config retrieved", view);
    }

    @PostMapping("/config")
    public ApiResponse<PaymentConfigView> upsertPaymentConfig(
            @Valid @RequestBody CreatePaymentConfigRequestDto request,
            @AuthenticationPrincipal UserPrincipal principal) {
        SchoolPaymentConfig c = paymentConfigService.upsertConfig(
                principal, request.shortcode(), request.shortcodeType(),
                request.environment(), request.consumerKey(),
                request.consumerSecret(), request.passkey());
        PaymentConfigView view = new PaymentConfigView(
                c.getId(), c.getSchoolId(), c.getShortcode(), c.getShortcodeType(),
                c.getEnvironment().name(), c.getEnabled(), true, c.getCreatedAt());
        return ApiResponse.success("Payment config updated", view);
    }

    @PatchMapping("/config/toggle")
    public ApiResponse<PaymentConfigView> togglePaymentConfig(
            @RequestParam boolean enabled,
            @AuthenticationPrincipal UserPrincipal principal) {
        SchoolPaymentConfig c = paymentConfigService.toggleEnabled(principal, enabled);
        PaymentConfigView view = new PaymentConfigView(
                c.getId(), c.getSchoolId(), c.getShortcode(), c.getShortcodeType(),
                c.getEnvironment().name(), c.getEnabled(),
                c.getEncryptedConsumerKey() != null, c.getCreatedAt());
        return ApiResponse.success("Payment config toggled", view);
    }

    @PostMapping("/config/rotate-token")
    public ApiResponse<String> rotateCallbackToken(
            @AuthenticationPrincipal UserPrincipal principal) {
        String token = paymentConfigService.rotateCallbackToken(principal);
        return ApiResponse.success("Callback token rotated. Save this token — it won't be shown again.", token);
    }

    @PostMapping("/config/test")
    public ApiResponse<Boolean> testConnection(
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean ok = paymentConfigService.testConnection(principal);
        return ApiResponse.success("Test connection result", ok);
    }

    // ==================== Unallocated Payments ====================

    @GetMapping("/unallocated")
    public ApiResponse<PageResponse<UnallocatedPaymentView>> listUnallocatedPayments(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(
                "Unallocated payments retrieved",
                PageResponse.from(unallocatedPaymentService.listUnallocated(principal, pageable)));
    }

    @GetMapping("/unallocated/all")
    public ApiResponse<PageResponse<UnallocatedPaymentView>> listAllUnallocatedPayments(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(
                "All unallocated payments retrieved",
                PageResponse.from(unallocatedPaymentService.listAll(principal, pageable)));
    }

    @PostMapping("/unallocated/{id}/allocate")
    public ApiResponse<UnallocatedPaymentView> allocateUnallocatedPayment(
            @PathVariable Long id,
            @Valid @RequestBody AllocatePaymentRequestDto request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UnallocatedPaymentView view = unallocatedPaymentService.allocateToStudent(
                principal, id, request.studentId(), request.reason());
        return ApiResponse.success("Payment allocated", view);
    }

    @PostMapping("/unallocated/{id}/refund")
    public ApiResponse<UnallocatedPaymentView> refundUnallocatedPayment(
            @PathVariable Long id,
            @RequestBody(required = false) String reason,
            @AuthenticationPrincipal UserPrincipal principal) {
        UnallocatedPaymentView view = unallocatedPaymentService.markForRefund(
                principal, id, reason);
        return ApiResponse.success("Payment marked for refund", view);
    }

    // ==================== Reports (SCHOOL_ADMIN, ACCOUNTANT) ====================

    @GetMapping("/reports/collection")
    public ApiResponse<List<CollectionByDayView>> collectionReport(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<CollectionByDayView> report = feeReportService.collectionByDay(principal, from, to);
        return ApiResponse.success("Collection report retrieved", report);
    }

    @GetMapping("/reports/collection/by-channel")
    public ApiResponse<List<CollectionByChannelView>> collectionByChannelReport(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<CollectionByChannelView> report = feeReportService.collectionByChannel(principal, from, to);
        return ApiResponse.success("Collection by channel report retrieved", report);
    }

    @GetMapping("/reports/arrears")
    public ApiResponse<List<ArrearsByClassLevelView>> arrearsReport(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<ArrearsByClassLevelView> report = feeReportService.arrearsByClassLevel(principal);
        return ApiResponse.success("Arrears report retrieved", report);
    }

    @GetMapping("/reports/reconciliation")
    public ApiResponse<List<ReconciliationDayView>> reconciliationReport(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<ReconciliationDayView> report = feeReportService.dailyReconciliation(principal, from, to);
        return ApiResponse.success("Reconciliation report retrieved", report);
    }

    @GetMapping("/reports/export")
    public ApiResponse<String> exportCollectionCsv(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<CollectionByDayView> report = feeReportService.collectionByDay(principal, from, to);

        List<String> headers = List.of("Date", "Total Collected", "Payment Count", "Currency");
        List<List<String>> rows = new ArrayList<>();
        for (CollectionByDayView row : report) {
            rows.add(List.of(
                    row.date().toString(),
                    row.totalCollected().toPlainString(),
                    String.valueOf(row.paymentCount()),
                    row.currency()
            ));
        }
        String csv = CsvExporter.toCsvWithBom(headers, rows);
        return ApiResponse.success("CSV export generated", csv);
    }
}
