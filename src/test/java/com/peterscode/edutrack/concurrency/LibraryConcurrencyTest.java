package com.peterscode.edutrack.concurrency;

import com.peterscode.edutrack.library.dto.LibraryRequests;
import com.peterscode.edutrack.library.entity.Book;
import com.peterscode.edutrack.library.entity.BookCopy;
import com.peterscode.edutrack.library.entity.Loan;
import com.peterscode.edutrack.library.repository.BookCopyRepository;
import com.peterscode.edutrack.library.repository.BookRepository;
import com.peterscode.edutrack.library.repository.LoanRepository;
import com.peterscode.edutrack.library.service.LibraryBorrowingService;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.repository.StudentRepository;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.entity.User;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency tests for library borrowing operations.
 * These tests are expected to FAIL on the current code because LibraryBorrowingService
 * has no pessimistic locks — concurrent threads can both see AVAILABLE status and
 * create duplicate loans.
 */
@Tag("concurrency")
class LibraryConcurrencyTest extends ConcurrencyTestBase {

    @Autowired
    private LibraryBorrowingService borrowingService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookCopyRepository bookCopyRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private StudentRepository studentRepository;

    private UserPrincipal librarian;
    private String bookNumber;
    private String studentAdmissionNumber;

    @org.junit.jupiter.api.BeforeEach
    void setupLibrary() {
        // Create a librarian user
        User libUser = User.builder()
                .firstName("Librarian")
                .lastName("Test")
                .username("librarian-" + schoolId)
                .email("librarian-" + schoolId + "@test.edu")
                .password("Password123!")
                .role(Role.LIBRARIAN)
                .schoolId(schoolId)
                .active(true)
                .build();
        libUser = userRepository.save(libUser);
        librarian = UserPrincipal.create(libUser);

        // Create a book
        Book book = Book.builder()
                .schoolId(schoolId)
                .title("Concurrency Test Book")
                .isbn("978-0-000-00000-0")
                .authors("Test Author")
                .categoryId(1L)
                .build();
        book = bookRepository.save(book);

        // Create a book copy
        bookNumber = "CONC-" + schoolId + "-001";
        BookCopy copy = BookCopy.builder()
                .schoolId(schoolId)
                .bookId(book.getId())
                .bookNumber(bookNumber)
                .status("AVAILABLE")
                .condition("GOOD")
                .acquiredOn(LocalDate.now())
                .build();
        bookCopyRepository.save(copy);

        // Create a student for borrowing
        studentAdmissionNumber = "STU-" + schoolId + "-001";
        Student student = Student.builder()
                .schoolId(schoolId)
                .firstName("Borrower")
                .lastName("Student")
                .admissionNumber(studentAdmissionNumber)
                .status(com.peterscode.edutrack.student.enums.StudentStatus.ACTIVE)
                .build();
        studentRepository.save(student);
    }

    /**
     * Test: 10 threads try to issue the same copy simultaneously.
     * Expected: Exactly 1 succeeds, 9 get a conflict/error.
     * Current code: Multiple threads can succeed because there's no pessimistic lock.
     */
    @Test
    void doubleIssue_shouldAllowOnlyOneWinner() throws Exception {
        int threads = 10;
        TestResult<Object> result = runConcurrent(threads, () -> {
            LibraryRequests.IssueRequest req = new LibraryRequests.IssueRequest(bookNumber, studentAdmissionNumber);
            borrowingService.issueBook(schoolId, req, librarian);
            return "issued";
        }, 30);

        // After all threads complete, check the database
        List<Loan> activeLoans = loanRepository.findAll().stream()
                .filter(l -> "ACTIVE".equals(l.getStatus()))
                .toList();

        assertThat(activeLoans.size())
                .as("Only one loan should be active — concurrent issue must be serialized")
                .isEqualTo(1);

        assertThat(result.successCount)
                .as("Exactly one thread should succeed")
                .isEqualTo(1);

        // No unhandled errors (500s) — losers should get clean conflicts
        assertThat(result.errorCount)
                .as("No unhandled errors from concurrent issue")
                .isZero();
    }

    /**
     * Test: Thread A issues a book, Thread B marks it lost simultaneously.
     * Expected: One winner, no deadlock error reaching the client.
     */
    @Test
    void issueVsLost_shouldNotDeadlock() throws Exception {
        // Pre-issue the book so both operations have something to work with
        LibraryRequests.IssueRequest req = new LibraryRequests.IssueRequest(bookNumber, studentAdmissionNumber);
        borrowingService.issueBook(schoolId, req, librarian);

        // Now race: return vs lost
        TestResult<Object> result = runConcurrent(2, () -> {
            try {
                borrowingService.returnBook(schoolId,
                        new LibraryRequests.ReturnRequest(bookNumber, null), librarian);
                return "returned";
            } catch (Exception e) {
                if (isConflictException(e)) {
                    return "conflict";
                }
                throw e;
            }
        }, 30);

        // Verify no deadlock error reached the client
        assertThat(result.errorCount)
                .as("No deadlock error should reach the client")
                .isZero();
    }

    /**
     * Test: Thread A returns a book, Thread B renews the same loan simultaneously.
     * Expected: One winner, no error.
     */
    @Test
    void returnVsRenew_shouldNotCorruptState() throws Exception {
        // Pre-issue the book
        LibraryRequests.IssueRequest req = new LibraryRequests.IssueRequest(bookNumber, studentAdmissionNumber);
        var loanView = borrowingService.issueBook(schoolId, req, librarian);
        Long loanId = loanView.id();

        TestResult<Object> result = runConcurrent(2, () -> {
            try {
                borrowingService.renewBook(schoolId, loanId, librarian);
                return "renewed";
            } catch (Exception e) {
                if (isConflictException(e)) {
                    return "conflict";
                }
                throw e;
            }
        }, 30);

        // Verify the loan is in a consistent state
        Loan loan = loanRepository.findBySchoolIdAndId(schoolId, loanId).orElse(null);
        assertThat(loan).isNotNull();
        assertThat(loan.getStatus()).isIn("ACTIVE", "RETURNED");

        assertThat(result.errorCount)
                .as("No unhandled errors from return vs renew race")
                .isZero();
    }
}
