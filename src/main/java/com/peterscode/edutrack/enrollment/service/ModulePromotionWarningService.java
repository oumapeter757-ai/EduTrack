package com.peterscode.edutrack.enrollment.service;

import com.peterscode.edutrack.fees.repository.StudentFeeAccountRepository;
import com.peterscode.edutrack.library.repository.LoanRepository;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ModulePromotionWarningService implements PromotionWarningService {
    private final SchoolRepository schools;
    private final StudentFeeAccountRepository feeAccounts;
    private final LoanRepository loans;

    @Override
    public WarningFlags warnings(Long schoolId, Long studentId) {
        var school = schools.findById(schoolId).orElseThrow();
        boolean fees = school.getEnabledModules().contains(ServiceModule.BILLING)
                && feeAccounts.findBySchoolIdAndStudentId(schoolId, studentId)
                .map(a -> a.getCachedBalance() != null && a.getCachedBalance().signum() > 0).orElse(false);
        boolean library = school.getEnabledModules().contains(ServiceModule.LIBRARY)
                && loans.countActiveByBorrower(schoolId, "STUDENT", studentId) > 0;
        return WarningFlags.builder().unpaidFees(fees).openLibraryLoans(library).incompleteMarks(false).build();
    }
}
