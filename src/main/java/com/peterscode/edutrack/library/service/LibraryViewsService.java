package com.peterscode.edutrack.library.service;

import com.peterscode.edutrack.library.audit.LibraryAuditService;
import com.peterscode.edutrack.library.dto.LibraryViews;
import com.peterscode.edutrack.library.entity.Book;
import com.peterscode.edutrack.library.entity.BookCopy;
import com.peterscode.edutrack.library.entity.LibraryCategory;
import com.peterscode.edutrack.library.entity.Loan;
import com.peterscode.edutrack.library.exception.LibraryRuleException;
import com.peterscode.edutrack.library.repository.*;
import com.peterscode.edutrack.guardian.repository.GuardianRepository;
import com.peterscode.edutrack.guardian.repository.ParentStudentMappingRepository;
import com.peterscode.edutrack.security.ParentAccessGuard;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.staff.entity.Staff;
import com.peterscode.edutrack.staff.repository.StaffRepository;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.repository.StudentRepository;
import com.peterscode.edutrack.user.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LibraryViewsService {

    private final LoanRepository loanRepository;
    private final LibraryFineRepository fineRepository;
    private final BookCopyRepository copyRepository;
    private final BookRepository bookRepository;
    private final LibraryCategoryRepository categoryRepository;
    private final StudentRepository studentRepository;
    private final StaffRepository staffRepository;
    private final GuardianRepository guardianRepository;
    private final ParentStudentMappingRepository parentStudentMappingRepository;
    private final LibraryAuditService auditService;
    private final Clock clock;

    // ==================== Borrower Lookup ====================

    @Transactional(readOnly = true)
    public LibraryViews.BorrowerProfileView lookupBorrower(Long schoolId, String borrowerNumber,
                                                            UserPrincipal principal) {
        String normalized = borrowerNumber.trim().toUpperCase();

        // Try student
        Student student = studentRepository.findBySchoolIdAndAdmissionNumber(schoolId, normalized).orElse(null);
        if (student != null) {
            LibraryViews.BorrowerStandingView standing = getStanding(schoolId, "STUDENT", student.getId());
            boolean eligible = student.isEnrolled();
            String reason = eligible ? null : "Student is not actively enrolled";

            if (eligible && standing.overdueLoans() > 0) {
                eligible = false;
                reason = "Has overdue loans";
            }
            if (eligible && standing.unpaidFineTotal().compareTo(BigDecimal.ZERO) > 0) {
                eligible = false;
                reason = "Has unpaid fines";
            }

            String classInfo = student.getStreamId() != null
                    ? "Stream " + student.getStreamId()
                    : (student.getGradeLevel() != null ? "Grade " + student.getGradeLevel() : "");

            auditService.append(schoolId, "BORROWER_LOOKUP", student.getId(), "LOOKUP", principal.getId(),
                    null, "borrowerNumber=" + normalized, null);

            return new LibraryViews.BorrowerProfileView(
                    student.getFirstName() + " " + student.getLastName(),
                    student.getAdmissionNumber(),
                    "STUDENT",
                    classInfo,
                    student.getStatus().name(),
                    eligible,
                    reason
            );
        }

        // Try staff
        Staff staff = staffRepository.findBySchoolIdAndStaffNumber(schoolId, normalized).orElse(null);
        if (staff != null && "ACTIVE".equals(staff.getStatus())) {
            LibraryViews.BorrowerStandingView standing = getStanding(schoolId, "STAFF", staff.getId());
            boolean eligible = "ACTIVE".equals(staff.getStatus());
            String reason = null;

            if (standing.overdueLoans() > 0) {
                eligible = false;
                reason = "Has overdue loans";
            }

            auditService.append(schoolId, "BORROWER_LOOKUP", staff.getId(), "LOOKUP", principal.getId(),
                    null, "borrowerNumber=" + normalized, null);

            return new LibraryViews.BorrowerProfileView(
                    staff.getFirstName() + " " + staff.getLastName(),
                    staff.getStaffNumber(),
                    "STAFF",
                    staff.getDepartment() != null ? staff.getDepartment() : "",
                    staff.getStatus().name(),
                    eligible,
                    reason
            );
        }

        throw new LibraryRuleException("BORROWER_NOT_FOUND", HttpStatus.NOT_FOUND,
                "Borrower not found");
    }

    @Transactional(readOnly = true)
    public Page<LibraryViews.LoanHistoryView> loanHistory(Long schoolId, String borrowerNumber,
                                                           Pageable pageable, UserPrincipal principal) {
        String normalized = borrowerNumber.trim().toUpperCase();

        Student student = studentRepository.findBySchoolIdAndAdmissionNumber(schoolId, normalized).orElse(null);
        if (student != null) {
            return mapLoans(loanRepository.findByBorrower(schoolId, "STUDENT", student.getId(), pageable));
        }

        Staff staff = staffRepository.findBySchoolIdAndStaffNumber(schoolId, normalized).orElse(null);
        if (staff != null) {
            return mapLoans(loanRepository.findByBorrower(schoolId, "STAFF", staff.getId(), pageable));
        }

        throw new LibraryRuleException("BORROWER_NOT_FOUND", HttpStatus.NOT_FOUND,
                "Borrower not found");
    }

    // ==================== Self-Service ====================

    @Transactional(readOnly = true)
    public Page<LibraryViews.LoanHistoryView> myLoans(Long schoolId, UserPrincipal principal) {
        if (principal.getRole() == Role.STUDENT) {
            Student student = studentRepository.findBySchoolIdAndUserId(schoolId, principal.getId()).orElse(null);
            if (student == null) {
                throw new LibraryRuleException("BORROWER_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Student record not found");
            }
            return mapLoans(loanRepository.findByBorrower(schoolId, "STUDENT", student.getId(),
                    Pageable.ofSize(20)));
        } else if (principal.getRole() == Role.TEACHER) {
            Staff staff = staffRepository.findBySchoolId(schoolId, Pageable.ofSize(1))
                    .stream().filter(s -> s.getUserId().equals(principal.getId())).findFirst().orElse(null);
            if (staff == null) {
                throw new LibraryRuleException("BORROWER_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Staff record not found");
            }
            return mapLoans(loanRepository.findByBorrower(schoolId, "STAFF", staff.getId(),
                    Pageable.ofSize(20)));
        }
        throw new LibraryRuleException("ACCESS_DENIED", HttpStatus.FORBIDDEN,
                "Role not supported for self-service");
    }

    // ==================== Parent Access ====================

    @Transactional(readOnly = true)
    public Page<LibraryViews.LoanHistoryView> childLoans(Long schoolId, UserPrincipal principal) {
        List<Long> linkedStudentIds = ParentAccessGuard.getLinkedStudentIds(
                schoolId, principal, parentStudentMappingRepository, guardianRepository);
        if (linkedStudentIds.isEmpty()) {
            throw new LibraryRuleException("BORROWER_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "No linked student found");
        }
        // Return loans for the first linked child
        Long studentId = linkedStudentIds.get(0);
        return mapLoans(loanRepository.findByBorrower(schoolId, "STUDENT", studentId,
                Pageable.ofSize(20)));
    }

    // ==================== Clearance ====================

    @Transactional(readOnly = true)
    public LibraryViews.ClearanceView checkClearance(Long schoolId, String admissionNumber,
                                                      UserPrincipal principal) {
        String normalized = admissionNumber.trim().toUpperCase();
        Student student = studentRepository.findBySchoolIdAndAdmissionNumber(schoolId, normalized).orElse(null);
        if (student == null) {
            throw new LibraryRuleException("BORROWER_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "Student not found");
        }

        List<String> reasons = new ArrayList<>();
        LibraryViews.BorrowerStandingView standing = getStanding(schoolId, "STUDENT", student.getId());

        if (standing.activeLoans() > 0) {
            reasons.add("Has " + standing.activeLoans() + " active loan(s)");
        }
        if (standing.overdueLoans() > 0) {
            reasons.add("Has " + standing.overdueLoans() + " overdue loan(s)");
        }
        if (standing.unpaidFineTotal().compareTo(BigDecimal.ZERO) > 0) {
            reasons.add("Unpaid fines: " + standing.unpaidFineTotal());
        }

        return new LibraryViews.ClearanceView(reasons.isEmpty(), reasons);
    }

    // ==================== Reports ====================

    @Transactional(readOnly = true)
    public Page<LibraryViews.LoanHistoryView> overdueLoans(Long schoolId, Pageable pageable,
                                                            UserPrincipal principal) {
        LocalDate today = LocalDate.now(clock);
        Page<Loan> overdue = loanRepository.findOverdue(schoolId, today, pageable);
        return mapLoans(overdue);
    }

    // ==================== Helpers ====================

    private LibraryViews.BorrowerStandingView getStanding(Long schoolId, String borrowerType, Long borrowerId) {
        long activeLoans = loanRepository.countActiveByBorrower(schoolId, borrowerType, borrowerId);
        LocalDate today = LocalDate.now(clock);
        long overdueLoans = loanRepository.findOverdueByBorrower(schoolId, borrowerType, borrowerId, today).size();
        BigDecimal unpaidTotal = fineRepository.sumUnpaidByBorrower(schoolId, borrowerType, borrowerId);
        return new LibraryViews.BorrowerStandingView(activeLoans, overdueLoans, unpaidTotal);
    }

    private Page<LibraryViews.LoanHistoryView> mapLoans(Page<Loan> loans) {
        return loans.map(loan -> {
            BookCopy copy = copyRepository.findById(loan.getCopyId()).orElse(null);
            Book book = copy != null ? bookRepository.findById(copy.getBookId()).orElse(null) : null;
            LibraryCategory category = book != null
                    ? categoryRepository.findBySchoolIdAndId(loan.getSchoolId(), book.getCategoryId()).orElse(null)
                    : null;

            String fineStatus = null;
            BigDecimal fineAmount = null;
            List<com.peterscode.edutrack.library.entity.LibraryFine> fines =
                    fineRepository.findUnresolvedByBorrower(loan.getSchoolId(), loan.getBorrowerType(), loan.getBorrowerId());
            for (com.peterscode.edutrack.library.entity.LibraryFine f : fines) {
                if (f.getLoanId().equals(loan.getId())) {
                    fineStatus = f.getStatus();
                    fineAmount = f.getAmount();
                    break;
                }
            }

            return new LibraryViews.LoanHistoryView(
                    loan.getId(),
                    book != null ? book.getTitle() : null,
                    copy != null ? copy.getBookNumber() : null,
                    category != null ? category.getName() : null,
                    loan.getIssuedOn(),
                    loan.getDueDate(),
                    loan.getReturnedAt(),
                    loan.getStatus(),
                    fineStatus,
                    fineAmount
            );
        });
    }
}
