package com.peterscode.edutrack.fees.service;

import com.peterscode.edutrack.fees.dto.FeeInvoiceView;
import com.peterscode.edutrack.fees.entity.FeeInvoice;
import com.peterscode.edutrack.fees.entity.FeeInvoiceLine;
import com.peterscode.edutrack.fees.entity.FeeItem;
import com.peterscode.edutrack.fees.entity.FeeStructure;
import com.peterscode.edutrack.fees.entity.StudentFeeAccount;
import com.peterscode.edutrack.fees.enums.FeeInvoiceStatus;
import com.peterscode.edutrack.fees.exception.FeesRuleException;
import com.peterscode.edutrack.fees.repository.FeeInvoiceRepository;
import com.peterscode.edutrack.fees.repository.FeeStructureRepository;
import com.peterscode.edutrack.fees.repository.StudentFeeAccountRepository;
import com.peterscode.edutrack.fees.security.FeesAccessGuard;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.security.ParentAccessGuard;
import com.peterscode.edutrack.guardian.repository.GuardianRepository;
import com.peterscode.edutrack.guardian.repository.ParentStudentMappingRepository;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.repository.StudentRepository;
import com.peterscode.edutrack.timetable.entity.Term;
import com.peterscode.edutrack.timetable.repository.TermRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class FeeInvoiceService {

    private final FeeInvoiceRepository feeInvoiceRepository;
    private final FeeStructureRepository feeStructureRepository;
    private final StudentFeeAccountRepository studentFeeAccountRepository;
    private final StudentRepository studentRepository;
    private final TermRepository termRepository;
    private final LedgerService ledgerService;
    private final GuardianRepository guardianRepository;
    private final ParentStudentMappingRepository parentStudentMappingRepository;

    /**
     * Generate invoices for all students in a class level for a given fee structure.
     * Idempotent: if an invoice already exists for (school, student, term, structure), it is skipped.
     */
    @Transactional
    public int generateInvoices(UserPrincipal principal, Long structureId) {
        FeesAccessGuard.assertAdminOrAccountant(principal);
        Long schoolId = principal.getSchoolId();

        FeeStructure structure = feeStructureRepository.findBySchoolIdAndId(schoolId, structureId)
                .orElseThrow(() -> new FeesRuleException("RESOURCE_NOT_FOUND", NOT_FOUND,
                        "Fee structure not found"));

        // Find all active students in the class level
        List<Student> students = studentRepository.findBySchoolIdAndClassLevelIdAndStatus(
                schoolId, structure.getClassLevelId(),
                com.peterscode.edutrack.student.enums.StudentStatus.ACTIVE);

        int count = 0;
        LocalDate today = LocalDate.now();
        for (Student student : students) {
            if (feeInvoiceRepository.existsBySchoolIdAndStudentIdAndTermIdAndStructureId(
                    schoolId, student.getId(), structure.getTermId(), structure.getId())) {
                continue; // already generated
            }

            // Ensure fee account exists
            StudentFeeAccount account = studentFeeAccountRepository
                    .findBySchoolIdAndStudentId(schoolId, student.getId())
                    .orElseGet(() -> studentFeeAccountRepository.save(
                            StudentFeeAccount.builder()
                                    .schoolId(schoolId)
                                    .studentId(student.getId())
                                    .build()));

            List<FeeInvoiceLine> lines = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            for (FeeItem item : structure.getItems()) {
                lines.add(FeeInvoiceLine.builder()
                        .invoiceId(null) // set after save
                        .name(item.getName())
                        .amount(item.getAmount())
                        .build());
                total = total.add(item.getAmount());
            }

            String invoiceNumber = generateInvoiceNumber(schoolId, student.getAdmissionNumber(), today);

            Term term = termRepository.findBySchoolIdAndId(schoolId, structure.getTermId())
                    .orElseThrow(() -> new FeesRuleException("RESOURCE_NOT_FOUND", NOT_FOUND,
                            "Term not found"));

            FeeInvoice invoice = FeeInvoice.builder()
                    .schoolId(schoolId)
                    .studentId(student.getId())
                    .termId(structure.getTermId())
                    .structureId(structure.getId())
                    .invoiceNumber(invoiceNumber)
                    .total(total)
                    .currency(account.getCurrency())
                    .issuedOn(today)
                    .dueOn(term.getEndDate())
                    .lines(lines)
                    .build();

            invoice = feeInvoiceRepository.save(invoice);

            // Set invoiceId on lines
            for (FeeInvoiceLine line : lines) {
                line.setInvoiceId(invoice.getId());
            }

            // Post charge entries to ledger
            for (FeeInvoiceLine line : lines) {
                ledgerService.postCharge(account, invoice, line.getName(), line.getAmount());
            }

            count++;
        }

        return count;
    }

    @Transactional(readOnly = true)
    public Page<FeeInvoiceView> listByStudent(UserPrincipal principal, Long studentId, Pageable pageable) {
        FeesAccessGuard.assertAnyFeeViewer(principal);
        Long schoolId = principal.getSchoolId();

        // Student can only see their own invoices
        if (principal.getRole() == com.peterscode.edutrack.user.entity.Role.STUDENT) {
            Long userId = principal.getId();
            Student student = studentRepository.findBySchoolIdAndUserId(schoolId, userId)
                    .orElseThrow(() -> new FeesRuleException("RESOURCE_NOT_FOUND", NOT_FOUND,
                            "Student not found"));
            studentId = student.getId();
        } else if (principal.getRole() == Role.PARENT) {
            ParentAccessGuard.assertLinked(schoolId, principal, parentStudentMappingRepository,
                    guardianRepository, studentId);
        }

        // For PARENT role, ParentAccessGuard is called by the controller before this method
        // For SCHOOL_ADMIN/ACCOUNTANT, studentId is provided directly

        return feeInvoiceRepository.findBySchoolIdAndStudentId(schoolId, studentId, pageable)
                .map(this::toView);
    }

    @Transactional(readOnly = true)
    public Page<FeeInvoiceView> listAll(UserPrincipal principal, Pageable pageable) {
        FeesAccessGuard.assertAdminOrAccountant(principal);
        Long schoolId = principal.getSchoolId();
        return feeInvoiceRepository.findBySchoolId(schoolId, pageable).map(this::toView);
    }

    @Transactional(readOnly = true)
    public FeeInvoiceView getById(UserPrincipal principal, Long id) {
        FeesAccessGuard.assertAnyFeeViewer(principal);
        Long schoolId = principal.getSchoolId();
        FeeInvoice invoice = feeInvoiceRepository.findBySchoolIdAndId(schoolId, id)
                .orElseThrow(() -> new FeesRuleException("RESOURCE_NOT_FOUND", NOT_FOUND,
                        "Invoice not found"));
        if (principal.getRole() == Role.STUDENT) {
            Long studentId = studentRepository.findBySchoolIdAndUserId(schoolId, principal.getId())
                    .orElseThrow(() -> new FeesRuleException("RESOURCE_NOT_FOUND", NOT_FOUND,
                            "Student not found")).getId();
            if (!studentId.equals(invoice.getStudentId())) {
                throw new FeesRuleException("RESOURCE_NOT_FOUND", NOT_FOUND, "Invoice not found");
            }
        } else if (principal.getRole() == Role.PARENT) {
            ParentAccessGuard.assertLinked(schoolId, principal, parentStudentMappingRepository,
                    guardianRepository, invoice.getStudentId());
        } else if (principal.getRole() != Role.SCHOOL_ADMIN
                && principal.getRole() != Role.ACCOUNTANT
                && principal.getRole() != Role.SUPER_ADMIN) {
            throw new FeesRuleException("INSUFFICIENT_PERMISSION", FORBIDDEN, "Access denied");
        }
        return toView(invoice);
    }

    private FeeInvoiceView toView(FeeInvoice invoice) {
        Student student = studentRepository.findBySchoolIdAndId(invoice.getSchoolId(), invoice.getStudentId())
                .orElse(null);
        String studentName = student != null ? student.getFullName() : "Unknown";

        List<FeeInvoiceView.FeeInvoiceLineView> lineViews = invoice.getLines().stream()
                .map(line -> new FeeInvoiceView.FeeInvoiceLineView(line.getName(), line.getAmount()))
                .toList();

        BigDecimal balance = invoice.getTotal().subtract(invoice.getPaid());

        return new FeeInvoiceView(
                invoice.getId(), invoice.getInvoiceNumber(), invoice.getStudentId(),
                studentName, invoice.getTermId(), invoice.getTotal(), invoice.getPaid(),
                balance, invoice.getCurrency(), invoice.getStatus(),
                invoice.getIssuedOn(), invoice.getDueOn(), lineViews);
    }

    private String generateInvoiceNumber(Long schoolId, String admissionNumber, LocalDate date) {
        String dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String cleanAdmission = admissionNumber.trim().toUpperCase().replaceAll("[\\s-]", "");
        return "INV-" + cleanAdmission + "-" + dateStr;
    }
}
