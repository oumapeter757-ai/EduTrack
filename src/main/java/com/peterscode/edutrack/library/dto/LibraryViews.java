package com.peterscode.edutrack.library.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class LibraryViews {

    public record CategoryView(Long id, String name, String code, Boolean active) {}

    public record BookView(Long id, String title, String authors, String isbn, String publisher,
                           Integer publicationYear, String edition, String language,
                           Boolean lendable, Boolean active, CategoryView category) {}

    public record AvailableBookView(Long id, String title, String authors, String isbn,
                                    CategoryView category, Integer publicationYear,
                                    Long availableCopies) {}

    public record CopyView(Long id, String bookNumber, String shelfLocation, String condition,
                           String status, LocalDate acquiredOn, BigDecimal lostChargeHint,
                           BookView book) {}

    public record AvailableCopyView(Long id, String bookNumber, String shelfLocation,
                                    String condition, String status) {}

    public record BatchCopyResult(Long successCount, Long failureCount, List<String> failures) {}

    public record LoanView(Long id, String bookNumber, String borrowerType, Long borrowerId,
                           Instant issuedOn, LocalDate dueDate, Instant returnedAt,
                           Integer renewalCount, String status, String copyStatus) {}

    public record FineView(Long id, String reason, Integer overdueDays,
                           BigDecimal suggestedAmount, BigDecimal amount, String status) {}

    public record ReturnResult(LoanView loan, FineView fine) {}

    public record LostResult(LoanView loan, FineView fine) {}

    public record BorrowerProfileView(String fullName, String borrowerNumber, String borrowerType,
                                      String classInfo, String status, Boolean eligibleToBorrow,
                                      String eligibilityReason) {}

    public record BorrowerStandingView(Long activeLoans, Long overdueLoans,
                                       BigDecimal unpaidFineTotal) {}

    public record LoanHistoryView(Long loanId, String bookTitle, String bookNumber,
                                  String categoryName, Instant issuedOn, LocalDate dueDate,
                                  Instant returnedAt, String status, String fineStatus,
                                  BigDecimal fineAmount) {}

    public record ClearanceView(Boolean cleared, List<String> reasons) {}
}
