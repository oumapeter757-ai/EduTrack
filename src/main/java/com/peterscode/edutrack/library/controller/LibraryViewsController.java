package com.peterscode.edutrack.library.controller;

import com.peterscode.edutrack.library.dto.LibraryViews;
import com.peterscode.edutrack.library.service.LibraryViewsService;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.security.RequiresModule;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.ApiResponse;
import com.peterscode.edutrack.shared.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/library")
@RequiredArgsConstructor
@RequiresModule(ServiceModule.LIBRARY)
public class LibraryViewsController {

    private final LibraryViewsService viewsService;

    // ==================== Borrower Lookup ====================

    @GetMapping("/borrowers/lookup")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'LIBRARIAN')")
    public ResponseEntity<ApiResponse<LibraryViews.BorrowerProfileView>> lookupBorrower(
            @RequestParam String borrowerNumber,
            @AuthenticationPrincipal UserPrincipal principal) {
        LibraryViews.BorrowerProfileView view = viewsService.lookupBorrower(
                principal.getSchoolId(), borrowerNumber, principal);
        return ResponseEntity.ok(ApiResponse.success("Borrower profile", view));
    }

    @GetMapping("/borrowers/{borrowerNumber}/loans")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'LIBRARIAN')")
    public ResponseEntity<ApiResponse<PageResponse<LibraryViews.LoanHistoryView>>> loanHistory(
            @PathVariable String borrowerNumber,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        Page<LibraryViews.LoanHistoryView> page = viewsService.loanHistory(
                principal.getSchoolId(), borrowerNumber, pageable, principal);
        return ResponseEntity.ok(ApiResponse.success("Loan history", PageResponse.from(page)));
    }

    // ==================== Self-Service ====================

    @GetMapping("/my-loans")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
    public ResponseEntity<ApiResponse<PageResponse<LibraryViews.LoanHistoryView>>> myLoans(
            @AuthenticationPrincipal UserPrincipal principal) {
        Page<LibraryViews.LoanHistoryView> page = viewsService.myLoans(
                principal.getSchoolId(), principal);
        return ResponseEntity.ok(ApiResponse.success("My loans", PageResponse.from(page)));
    }

    // ==================== Parent Access ====================

    @GetMapping("/parent/child-loans")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ApiResponse<PageResponse<LibraryViews.LoanHistoryView>>> childLoans(
            @AuthenticationPrincipal UserPrincipal principal) {
        Page<LibraryViews.LoanHistoryView> page = viewsService.childLoans(
                principal.getSchoolId(), principal);
        return ResponseEntity.ok(ApiResponse.success("Child loans", PageResponse.from(page)));
    }

    // ==================== Clearance ====================

    @GetMapping("/clearance")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'LIBRARIAN')")
    public ResponseEntity<ApiResponse<LibraryViews.ClearanceView>> checkClearance(
            @RequestParam String admissionNumber,
            @AuthenticationPrincipal UserPrincipal principal) {
        LibraryViews.ClearanceView view = viewsService.checkClearance(
                principal.getSchoolId(), admissionNumber, principal);
        return ResponseEntity.ok(ApiResponse.success("Clearance check", view));
    }

    // ==================== Reports ====================

    @GetMapping("/reports/overdue")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'LIBRARIAN')")
    public ResponseEntity<ApiResponse<PageResponse<LibraryViews.LoanHistoryView>>> overdueLoans(
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        Page<LibraryViews.LoanHistoryView> page = viewsService.overdueLoans(
                principal.getSchoolId(), pageable, principal);
        return ResponseEntity.ok(ApiResponse.success("Overdue loans", PageResponse.from(page)));
    }
}
