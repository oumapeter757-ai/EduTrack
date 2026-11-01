package com.peterscode.edutrack.fees.service;

import com.peterscode.edutrack.fees.dto.FeeStatementView;
import com.peterscode.edutrack.fees.dto.LinkedChildView;
import com.peterscode.edutrack.fees.dto.LedgerEntryView;
import com.peterscode.edutrack.fees.entity.StudentFeeAccount;
import com.peterscode.edutrack.fees.exception.FeesRuleException;
import com.peterscode.edutrack.fees.repository.LedgerEntryRepository;
import com.peterscode.edutrack.fees.repository.StudentFeeAccountRepository;
import com.peterscode.edutrack.fees.security.FeesAccessGuard;
import com.peterscode.edutrack.guardian.entity.ParentStudentMapping;
import com.peterscode.edutrack.guardian.repository.GuardianRepository;
import com.peterscode.edutrack.guardian.repository.ParentStudentMappingRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.repository.StudentRepository;
import com.peterscode.edutrack.user.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Provides paginated fee statements (ledger entries) for students.
 */
@Service
@RequiredArgsConstructor
public class FeeStatementService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final StudentFeeAccountRepository studentFeeAccountRepository;
    private final StudentRepository studentRepository;

    @Transactional(readOnly = true)
    public FeeStatementView getStatement(UserPrincipal principal, Long studentId,
                                          int page, int size) {
        FeesAccessGuard.assertAnyFeeViewer(principal);
        Long schoolId = principal.getSchoolId();

        // Student can only see their own statement
        if (principal.getRole() == Role.STUDENT) {
            Student student = studentRepository.findBySchoolIdAndUserId(schoolId, principal.getId())
                    .orElseThrow(() -> new FeesRuleException("RESOURCE_NOT_FOUND", NOT_FOUND,
                            "Student not found"));
            studentId = student.getId();
        }

        StudentFeeAccount account = studentFeeAccountRepository
                .findBySchoolIdAndStudentId(schoolId, studentId)
                .orElseThrow(() -> new FeesRuleException("ACCOUNT_NOT_FOUND", NOT_FOUND,
                        "Fee account not found"));

        // Whitelist sort fields
        Sort sort = Sort.by(Sort.Direction.DESC, "occurredAt");
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), sort);

        Page<LedgerEntryView> entriesPage = ledgerEntryRepository
                .findBySchoolIdAndAccountId(schoolId, account.getId(), pageable)
                .map(entry -> new LedgerEntryView(
                        entry.getId(), entry.getType(), entry.getAmount(),
                        entry.getDescription(), entry.getOccurredAt()));

        Student student = studentRepository.findBySchoolIdAndId(schoolId, studentId).orElse(null);
        String studentName = student != null ? student.getFullName() : "Unknown";

        return new FeeStatementView(
                studentId, studentName, account.getCurrency(),
                account.getCachedBalance(), entriesPage.getContent());
    }

    /**
     * List all linked children for a parent with their fee balance summary.
     */
    @Transactional(readOnly = true)
    public List<LinkedChildView> listLinkedChildren(
            UserPrincipal principal,
            GuardianRepository guardianRepo,
            ParentStudentMappingRepository mappingRepo) {
        FeesAccessGuard.assertParent(principal);
        Long schoolId = principal.getSchoolId();

        List<Long> linkedStudentIds = com.peterscode.edutrack.security.ParentAccessGuard
                .getLinkedStudentIds(schoolId, principal, mappingRepo, guardianRepo);

        List<LinkedChildView> result = new java.util.ArrayList<>();
        for (Long studentId : linkedStudentIds) {
            Student student = studentRepository.findBySchoolIdAndId(schoolId, studentId).orElse(null);
            if (student == null) continue;

            StudentFeeAccount account = studentFeeAccountRepository
                    .findBySchoolIdAndStudentId(schoolId, studentId)
                    .orElse(null);

            BigDecimal balance = account != null ? account.getCachedBalance() : BigDecimal.ZERO;
            String currency = account != null ? account.getCurrency() : "KES";

            result.add(new LinkedChildView(
                    studentId, student.getFullName(), student.getAdmissionNumber(),
                    balance, currency));
        }

        return result;
    }
}
