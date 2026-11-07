package com.peterscode.edutrack.fees.service;

import com.peterscode.edutrack.fees.dto.FeeInvoiceView;
import com.peterscode.edutrack.fees.dto.FeePaymentView;
import com.peterscode.edutrack.fees.dto.ManualPaymentRequestDto;
import com.peterscode.edutrack.fees.entity.FeeInvoice;
import com.peterscode.edutrack.fees.entity.FeePayment;
import com.peterscode.edutrack.fees.entity.PaymentAllocation;
import com.peterscode.edutrack.fees.entity.StudentFeeAccount;
import com.peterscode.edutrack.fees.enums.FeeInvoiceStatus;
import com.peterscode.edutrack.fees.enums.FeePaymentStatus;
import com.peterscode.edutrack.fees.exception.FeesRuleException;
import com.peterscode.edutrack.fees.repository.FeeInvoiceRepository;
import com.peterscode.edutrack.fees.repository.FeePaymentRepository;
import com.peterscode.edutrack.fees.repository.PaymentAllocationRepository;
import com.peterscode.edutrack.fees.repository.StudentFeeAccountRepository;
import com.peterscode.edutrack.fees.security.FeesAccessGuard;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.security.ParentAccessGuard;
import com.peterscode.edutrack.guardian.repository.GuardianRepository;
import com.peterscode.edutrack.guardian.repository.ParentStudentMappingRepository;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.*;

/**
 * The ONLY service that posts payments to the ledger and allocates to invoices.
 * Uses PESSIMISTIC_WRITE locks on StudentFeeAccount to prevent concurrent issues.
 */
@Service
@RequiredArgsConstructor
public class PaymentPostingService {

    private final FeePaymentRepository feePaymentRepository;
    private final FeeInvoiceRepository feeInvoiceRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final StudentFeeAccountRepository studentFeeAccountRepository;
    private final StudentRepository studentRepository;
    private final GuardianRepository guardianRepository;
    private final ParentStudentMappingRepository parentStudentMappingRepository;
    private final LedgerService ledgerService;
    private final Clock clock;

    /**
     * Record a manual payment and allocate to invoices.
     * Uses PESSIMISTIC_WRITE locks on StudentFeeAccount and open FeeInvoices
     * to prevent concurrent payment issues.
     */
    @Transactional
    public FeePaymentView recordManualPayment(UserPrincipal principal, ManualPaymentRequestDto request) {
        FeesAccessGuard.assertAdminOrAccountant(principal);
        Long schoolId = principal.getSchoolId();

        Student student = studentRepository.findBySchoolIdAndId(schoolId, request.studentId())
                .orElseThrow(() -> new FeesRuleException("RESOURCE_NOT_FOUND", NOT_FOUND,
                        "Student not found"));

        // Lock the fee account to get currency and serialize concurrent payments
        StudentFeeAccount account = studentFeeAccountRepository
                .findBySchoolIdAndStudentIdForUpdate(schoolId, student.getId())
                .orElseGet(() -> studentFeeAccountRepository.save(
                        StudentFeeAccount.builder()
                                .schoolId(schoolId)
                                .studentId(student.getId())
                                .build()));

        // Create payment record
        String receiptNumber = generateReceiptNumber(schoolId);
        FeePayment payment = FeePayment.builder()
                .schoolId(schoolId)
                .studentId(student.getId())
                .channel(request.channel())
                .amount(request.amount())
                .currency(account.getCurrency())
                .payerPhoneMasked(maskPhone(request.payerPhone()))
                .accountReference(request.accountReference())
                .status(FeePaymentStatus.SUCCEEDED)
                .initiatedBy(principal.getId())
                .receiptNumber(receiptNumber)
                .confirmedAt(Instant.now(clock))
                .build();

        payment = feePaymentRepository.save(payment);

        // Allocate to open invoices — invoices are locked inside doAllocatePaymentToInvoices
        doAllocatePaymentToInvoices(account, payment);

        // Update cached balance
        ledgerService.updateCachedBalance(account);
        studentFeeAccountRepository.save(account);

        return toView(payment, student);
    }

    /**
     * Allocate a payment to open invoices, oldest first.
     * Acquires PESSIMISTIC_WRITE locks on both the StudentFeeAccount and open FeeInvoices
     * to prevent double-allocation under concurrent payment scenarios.
     * This is the primary entry point — all callers should use this overload.
     */
    @Transactional
    public void allocatePaymentToInvoices(Long schoolId, Long studentId, FeePayment payment) {
        // Lock the fee account with PESSIMISTIC_WRITE to serialize concurrent payments
        StudentFeeAccount account = studentFeeAccountRepository
                .findBySchoolIdAndStudentIdForUpdate(schoolId, studentId)
                .orElseGet(() -> studentFeeAccountRepository.save(
                        StudentFeeAccount.builder()
                                .schoolId(schoolId)
                                .studentId(studentId)
                                .build()));

        doAllocatePaymentToInvoices(account, payment);

        // Update cached balance
        ledgerService.updateCachedBalance(account);
        studentFeeAccountRepository.save(account);
    }

    /**
     * Legacy overload for backward compatibility — delegates to the primary method.
     * @deprecated Use {@link #allocatePaymentToInvoices(Long, Long, FeePayment)} instead.
     */
    @Deprecated
    @Transactional
    public void allocatePaymentToInvoices(StudentFeeAccount account, FeePayment payment) {
        allocatePaymentToInvoices(account.getSchoolId(), account.getStudentId(), payment);
    }

    private void doAllocatePaymentToInvoices(StudentFeeAccount account, FeePayment payment) {
        Long schoolId = account.getSchoolId();
        Long studentId = payment.getStudentId();

        List<FeeInvoice> openInvoices = feeInvoiceRepository
                .findBySchoolIdAndStudentIdAndStatusInOrderByIssuedOnAscForUpdate(
                        schoolId, studentId,
                        List.of(FeeInvoiceStatus.OPEN, FeeInvoiceStatus.PARTLY_PAID));

        BigDecimal remaining = payment.getAmount();
        List<PaymentAllocation> allocations = new ArrayList<>();

        for (FeeInvoice invoice : openInvoices) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal invoiceBalance = invoice.getTotal().subtract(invoice.getPaid());
            BigDecimal allocationAmount = remaining.min(invoiceBalance);

            PaymentAllocation allocation = PaymentAllocation.builder()
                    .paymentId(payment.getId())
                    .invoiceId(invoice.getId())
                    .amount(allocationAmount)
                    .build();
            allocations.add(allocation);

            // Post ledger entry
            ledgerService.postPayment(account, payment.getId(), invoice.getId(),
                    "Payment " + payment.getReceiptNumber(), allocationAmount);

            // Update invoice
            invoice.setPaid(invoice.getPaid().add(allocationAmount));
            if (invoice.getPaid().compareTo(invoice.getTotal()) >= 0) {
                invoice.setStatus(FeeInvoiceStatus.PAID);
            } else {
                invoice.setStatus(FeeInvoiceStatus.PARTLY_PAID);
            }
            feeInvoiceRepository.save(invoice);

            remaining = remaining.subtract(allocationAmount);
        }

        paymentAllocationRepository.saveAll(allocations);
    }

    @Transactional(readOnly = true)
    public Page<FeePaymentView> listByStudent(UserPrincipal principal, Long studentId, Pageable pageable) {
        FeesAccessGuard.assertAnyFeeViewer(principal);
        Long schoolId = principal.getSchoolId();

        // Student can only see their own payments
        if (principal.getRole() == com.peterscode.edutrack.user.entity.Role.STUDENT) {
            Student student = studentRepository.findBySchoolIdAndUserId(schoolId, principal.getId())
                    .orElseThrow(() -> new FeesRuleException("RESOURCE_NOT_FOUND", NOT_FOUND,
                            "Student not found"));
            studentId = student.getId();
        } else if (principal.getRole() == Role.PARENT) {
            ParentAccessGuard.assertLinked(schoolId, principal, parentStudentMappingRepository,
                    guardianRepository, studentId);
        }

        // For PARENT role, ParentAccessGuard is called by the controller before this method
        // For SCHOOL_ADMIN/ACCOUNTANT, studentId is provided directly

        Student student = resolveStudent(schoolId, studentId);
        return feePaymentRepository.findBySchoolIdAndStudentId(schoolId, studentId, pageable)
                .map(p -> toView(p, student));
    }

    @Transactional(readOnly = true)
    public Page<FeePaymentView> listAll(UserPrincipal principal, Pageable pageable) {
        FeesAccessGuard.assertAdminOrAccountant(principal);
        Long schoolId = principal.getSchoolId();
        Page<FeePayment> payments = feePaymentRepository.findBySchoolId(schoolId, pageable);
        Map<Long, Student> students = studentRepository.findAllBySchoolIdAndIdIn(
                        schoolId, payments.getContent().stream()
                                .map(FeePayment::getStudentId)
                                .filter(java.util.Objects::nonNull)
                                .collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Student::getId, Function.identity()));
        return payments.map(p -> toView(p, students.get(p.getStudentId())));
    }

    private FeePaymentView toView(FeePayment payment, Student student) {
        String name = student != null ? student.getFullName() : "Unknown";
        return new FeePaymentView(
                payment.getId(), payment.getStudentId(), name,
                payment.getChannel(), payment.getAmount(), payment.getCurrency(),
                payment.getPayerPhoneMasked(), payment.getStatus(),
                payment.getReceiptNumber(), payment.getConfirmedAt(), payment.getCreatedAt());
    }

    private Student resolveStudent(Long schoolId, Long studentId) {
        if (studentId == null) return null;
        return studentRepository.findBySchoolIdAndId(schoolId, studentId).orElse(null);
    }

    private String generateReceiptNumber(Long schoolId) {
        String timestamp = Instant.now(clock).toString().replace(":", "").substring(0, 15);
        return "RCP-" + schoolId + "-" + timestamp;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() <= 4) return phone;
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }
}
