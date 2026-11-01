package com.peterscode.edutrack.library.service;

import com.peterscode.edutrack.library.audit.LibraryAuditService;
import com.peterscode.edutrack.library.dto.LibraryRequests;
import com.peterscode.edutrack.library.dto.LibraryViews;
import com.peterscode.edutrack.library.entity.*;
import com.peterscode.edutrack.library.exception.LibraryRuleException;
import com.peterscode.edutrack.library.repository.*;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.staff.entity.Staff;
import com.peterscode.edutrack.staff.repository.StaffRepository;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.repository.StudentRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LibraryBorrowingService {

    private final BookCopyRepository copyRepository;
    private final LoanRepository loanRepository;
    private final LibraryBorrowerAccountRepository accountRepository;
    private final BorrowingPolicyRepository policyRepository;
    private final LibraryFineRepository fineRepository;
    private final StudentRepository studentRepository;
    private final StaffRepository staffRepository;
    private final LibraryAuditService auditService;
    private final EntityManager entityManager;
    private final Clock clock;

    // ==================== Issue ====================

    @Transactional
    public LibraryViews.LoanView issueBook(Long schoolId, LibraryRequests.IssueRequest request, UserPrincipal principal) {
        String bookNumber = request.bookNumber().trim().toUpperCase();

        // 1. Lock copy first
        BookCopy copy = copyRepository.findBySchoolIdAndBookNumber(schoolId, bookNumber)
                .orElseThrow(() -> new LibraryRuleException("BOOK_NUMBER_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Book copy not found"));
        if (!"AVAILABLE".equals(copy.getStatus())) {
            throw new LibraryRuleException("COPY_NOT_AVAILABLE", HttpStatus.CONFLICT,
                    "Copy is not available");
        }

        // Check existing active loan
        if (loanRepository.findActiveByCopyId(copy.getId()).isPresent()) {
            throw new LibraryRuleException("COPY_NOT_AVAILABLE", HttpStatus.CONFLICT,
                    "Copy is already on loan");
        }

        // 2. Resolve borrower
        BorrowerInfo borrower = resolveBorrower(schoolId, request.borrowerNumber());

        // 3. Lock borrower account
        LibraryBorrowerAccount account = getOrCreateAccount(schoolId, borrower.type(), borrower.id());

        // 4. Get policy
        BorrowingPolicy policy = resolvePolicy(schoolId, borrower.type());

        // 5. Re-check all conditions inside locks
        long activeLoans = loanRepository.countActiveByBorrower(schoolId, borrower.type(), borrower.id());
        if (activeLoans >= policy.getMaxLoans()) {
            throw new LibraryRuleException("LOAN_LIMIT_REACHED", HttpStatus.CONFLICT,
                    "Borrower has reached maximum loan limit");
        }

        LocalDate today = LocalDate.now(clock);
        List<Loan> overdue = loanRepository.findOverdueByBorrower(schoolId, borrower.type(), borrower.id(), today);
        if (!overdue.isEmpty()) {
            throw new LibraryRuleException("BORROWER_HAS_OVERDUE", HttpStatus.CONFLICT,
                    "Borrower has overdue loans");
        }

        BigDecimal unpaidTotal = fineRepository.sumUnpaidByBorrower(schoolId, borrower.type(), borrower.id());
        if (unpaidTotal.compareTo(policy.getMaxUnpaidFines()) > 0) {
            throw new LibraryRuleException("UNPAID_FINES", HttpStatus.CONFLICT,
                    "Borrower has unpaid fines exceeding the allowed threshold");
        }

        // 6. Create loan
        LocalDate dueDate = today.plusDays(policy.getLoanDays());
        UUID activeMarker = UUID.randomUUID();

        Loan loan = Loan.builder()
                .schoolId(schoolId)
                .copyId(copy.getId())
                .borrowerType(borrower.type())
                .borrowerId(borrower.id())
                .issuedBy(principal.getId())
                .issuedOn(Instant.now(clock))
                .dueDate(dueDate)
                .renewalCount(0)
                .status("ACTIVE")
                .activeMarker(activeMarker)
                .build();
        loan = loanRepository.save(loan);

        // 7. Update copy and account
        copy.setStatus("ON_LOAN");
        copyRepository.save(copy);

        account.setActiveLoanCount(account.getActiveLoanCount() + 1);
        accountRepository.save(account);

        // 8. Audit
        auditService.append(schoolId, "LOAN", loan.getId(), "ISSUE", principal.getId(),
                null, "bookNumber=" + bookNumber + ",borrower=" + request.borrowerNumber(), null);

        return toLoanView(loan, copyRepository, entityManager);
    }

    // ==================== Return ====================

    @Transactional
    public LibraryViews.ReturnResult returnBook(Long schoolId, LibraryRequests.ReturnRequest request, UserPrincipal principal) {
        String bookNumber = request.bookNumber().trim().toUpperCase();

        BookCopy copy = copyRepository.findBySchoolIdAndBookNumber(schoolId, bookNumber)
                .orElseThrow(() -> new LibraryRuleException("BOOK_NUMBER_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Book copy not found"));

        Loan loan = loanRepository.findActiveByCopyId(copy.getId())
                .orElseThrow(() -> new LibraryRuleException("LOAN_ALREADY_CLOSED", HttpStatus.CONFLICT,
                        "No active loan for this copy"));

        // Mark returned
        loan.setStatus("RETURNED");
        loan.setReturnedAt(Instant.now(clock));
        loan.setReceivedBy(principal.getId());
        loan.setActiveMarker(null);
        loanRepository.save(loan);

        // Update copy
        String newStatus = "DAMAGED".equalsIgnoreCase(request.damage()) ? "DAMAGED" : "AVAILABLE";
        String oldStatus = copy.getStatus();
        copy.setStatus(newStatus);
        copyRepository.save(copy);

        // Decrement account
        LibraryBorrowerAccount account = accountRepository
                .findBySchoolIdAndBorrowerTypeAndBorrowerId(schoolId, loan.getBorrowerType(), loan.getBorrowerId())
                .orElseThrow(() -> new LibraryRuleException("BORROWER_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Borrower account not found"));
        account.setActiveLoanCount(Math.max(0, account.getActiveLoanCount() - 1));
        accountRepository.save(account);

        // Check overdue fine
        LibraryViews.FineView fineView = null;
        LocalDate today = LocalDate.now(clock);
        if (today.isAfter(loan.getDueDate())) {
            long overdueDays = ChronoUnit.DAYS.between(loan.getDueDate(), today);
            BorrowingPolicy policy = resolvePolicy(schoolId, loan.getBorrowerType());
            BigDecimal suggested = policy.getSuggestedFinePerDay().multiply(BigDecimal.valueOf(overdueDays));

            LibraryFine fine = LibraryFine.builder()
                    .schoolId(schoolId)
                    .loanId(loan.getId())
                    .borrowerType(loan.getBorrowerType())
                    .borrowerId(loan.getBorrowerId())
                    .reason("OVERDUE")
                    .overdueDays((int) overdueDays)
                    .suggestedAmount(suggested)
                    .status("PENDING_ASSESSMENT")
                    .build();
            fine = fineRepository.save(fine);
            fineView = toFineView(fine);

            auditService.append(schoolId, "FINE", fine.getId(), "CREATE", principal.getId(),
                    null, "reason=OVERDUE,days=" + overdueDays + ",suggested=" + suggested, "Overdue return");
        }

        auditService.append(schoolId, "LOAN", loan.getId(), "RETURN", principal.getId(),
                "status=ACTIVE", "status=RETURNED,damage=" + request.damage(), null);

        return new LibraryViews.ReturnResult(toLoanView(loan, copyRepository, entityManager), fineView);
    }

    // ==================== Renew ====================

    @Transactional
    public LibraryViews.LoanView renewBook(Long schoolId, Long loanId, UserPrincipal principal) {
        Loan loan = loanRepository.findBySchoolIdAndId(schoolId, loanId)
                .orElseThrow(() -> new LibraryRuleException("LOAN_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Loan not found"));
        if (!"ACTIVE".equals(loan.getStatus())) {
            throw new LibraryRuleException("LOAN_NOT_ACTIVE", HttpStatus.CONFLICT,
                    "Loan is not active");
        }

        LocalDate today = LocalDate.now(clock);
        if (today.isAfter(loan.getDueDate())) {
            throw new LibraryRuleException("LOAN_OVERDUE", HttpStatus.CONFLICT,
                    "Cannot renew overdue loan");
        }

        BorrowingPolicy policy = resolvePolicy(schoolId, loan.getBorrowerType());
        if (loan.getRenewalCount() >= policy.getMaxRenewals()) {
            throw new LibraryRuleException("RENEWAL_LIMIT_REACHED", HttpStatus.CONFLICT,
                    "Maximum renewals reached");
        }

        long daysRemaining = ChronoUnit.DAYS.between(today, loan.getDueDate());
        loan.setDueDate(today.plusDays(daysRemaining + policy.getLoanDays()));
        loan.setRenewalCount(loan.getRenewalCount() + 1);
        loan = loanRepository.save(loan);

        auditService.append(schoolId, "LOAN", loan.getId(), "RENEW", principal.getId(),
                "dueDate=" + loan.getDueDate() + ",renewals=" + (loan.getRenewalCount() - 1),
                "dueDate=" + loan.getDueDate() + ",renewals=" + loan.getRenewalCount(), null);

        return toLoanView(loan, copyRepository, entityManager);
    }

    // ==================== Lost ====================

    @Transactional
    public LibraryViews.LostResult markLost(Long schoolId, String bookNumber, UserPrincipal principal) {
        String normalized = bookNumber.trim().toUpperCase();

        BookCopy copy = copyRepository.findBySchoolIdAndBookNumber(schoolId, normalized)
                .orElseThrow(() -> new LibraryRuleException("BOOK_NUMBER_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Book copy not found"));

        Loan loan = loanRepository.findActiveByCopyId(copy.getId())
                .orElseThrow(() -> new LibraryRuleException("LOAN_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "No active loan for this copy"));

        // Mark loan lost
        loan.setStatus("LOST");
        loan.setActiveMarker(null);
        loanRepository.save(loan);

        // Mark copy lost
        copy.setStatus("LOST");
        copyRepository.save(copy);

        // Decrement account
        LibraryBorrowerAccount account = accountRepository
                .findBySchoolIdAndBorrowerTypeAndBorrowerId(schoolId, loan.getBorrowerType(), loan.getBorrowerId())
                .orElseThrow(() -> new LibraryRuleException("BORROWER_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Borrower account not found"));
        account.setActiveLoanCount(Math.max(0, account.getActiveLoanCount() - 1));
        accountRepository.save(account);

        // Create fine
        BorrowingPolicy policy = resolvePolicy(schoolId, loan.getBorrowerType());
        BigDecimal suggested = copy.getLostChargeHint() != null ? copy.getLostChargeHint()
                : policy.getSuggestedFinePerDay().multiply(BigDecimal.valueOf(policy.getLoanDays()));

        LibraryFine fine = LibraryFine.builder()
                .schoolId(schoolId)
                .loanId(loan.getId())
                .borrowerType(loan.getBorrowerType())
                .borrowerId(loan.getBorrowerId())
                .reason("LOST")
                .suggestedAmount(suggested)
                .status("PENDING_ASSESSMENT")
                .build();
        fine = fineRepository.save(fine);

        auditService.append(schoolId, "LOAN", loan.getId(), "LOST", principal.getId(),
                "status=ACTIVE", "status=LOST", "Book marked as lost");
        auditService.append(schoolId, "COPY", copy.getId(), "STATUS_CHANGE", principal.getId(),
                "status=" + copy.getStatus(), "status=LOST", "Lost with loan");

        return new LibraryViews.LostResult(toLoanView(loan, copyRepository, entityManager), toFineView(fine));
    }

    // ==================== Helpers ====================

    private record BorrowerInfo(String type, Long id) {}

    private BorrowerInfo resolveBorrower(Long schoolId, String borrowerNumber) {
        String normalized = borrowerNumber.trim().toUpperCase();

        // Try student first
        Student student = studentRepository.findBySchoolIdAndAdmissionNumber(schoolId, normalized).orElse(null);
        if (student != null && student.isEnrolled()) {
            return new BorrowerInfo("STUDENT", student.getId());
        }

        // Try staff
        Staff staff = staffRepository.findBySchoolIdAndStaffNumber(schoolId, normalized).orElse(null);
        if (staff != null && "ACTIVE".equals(staff.getStatus())) {
            return new BorrowerInfo("STAFF", staff.getId());
        }

        throw new LibraryRuleException("BORROWER_NOT_FOUND", HttpStatus.NOT_FOUND,
                "Borrower not found or not eligible");
    }

    private LibraryBorrowerAccount getOrCreateAccount(Long schoolId, String borrowerType, Long borrowerId) {
        return accountRepository.findBySchoolIdAndBorrowerTypeAndBorrowerId(schoolId, borrowerType, borrowerId)
                .orElseGet(() -> {
                    LibraryBorrowerAccount account = LibraryBorrowerAccount.builder()
                            .schoolId(schoolId)
                            .borrowerType(borrowerType)
                            .borrowerId(borrowerId)
                            .activeLoanCount(0)
                            .build();
                    return accountRepository.save(account);
                });
    }

    private BorrowingPolicy resolvePolicy(Long schoolId, String borrowerType) {
        return policyRepository.findBySchoolIdAndBorrowerType(schoolId, borrowerType)
                .orElseGet(() -> {
                    // Default policy
                    if ("STUDENT".equals(borrowerType)) {
                        return BorrowingPolicy.builder()
                                .schoolId(schoolId)
                                .borrowerType("STUDENT")
                                .maxLoans(3)
                                .loanDays(14)
                                .maxRenewals(2)
                                .suggestedFinePerDay(new BigDecimal("0.50"))
                                .maxUnpaidFines(new BigDecimal("10.00"))
                                .build();
                    } else {
                        return BorrowingPolicy.builder()
                                .schoolId(schoolId)
                                .borrowerType("STAFF")
                                .maxLoans(5)
                                .loanDays(14)
                                .maxRenewals(2)
                                .suggestedFinePerDay(new BigDecimal("0.50"))
                                .maxUnpaidFines(new BigDecimal("10.00"))
                                .build();
                    }
                });
    }

    private LibraryViews.LoanView toLoanView(Loan loan, BookCopyRepository copyRepo, EntityManager em) {
        BookCopy copy = copyRepo.findById(loan.getCopyId()).orElse(null);
        return new LibraryViews.LoanView(
                loan.getId(),
                copy != null ? copy.getBookNumber() : null,
                loan.getBorrowerType(),
                loan.getBorrowerId(),
                loan.getIssuedOn(),
                loan.getDueDate(),
                loan.getReturnedAt(),
                loan.getRenewalCount(),
                loan.getStatus(),
                copy != null ? copy.getStatus() : null
        );
    }

    private LibraryViews.FineView toFineView(LibraryFine fine) {
        return new LibraryViews.FineView(
                fine.getId(),
                fine.getReason(),
                fine.getOverdueDays(),
                fine.getSuggestedAmount(),
                fine.getAmount(),
                fine.getStatus()
        );
    }
}
