package com.peterscode.edutrack.library.controller;

import com.peterscode.edutrack.library.dto.LibraryRequests;
import com.peterscode.edutrack.library.dto.LibraryViews;
import com.peterscode.edutrack.library.service.LibraryBorrowingService;
import com.peterscode.edutrack.library.service.LibraryFineService;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.security.RequiresModule;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.RetryHelper;
import com.peterscode.edutrack.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/library")
@RequiredArgsConstructor
@RequiresModule(ServiceModule.LIBRARY)
public class LibraryBorrowingController {

    private final LibraryBorrowingService borrowingService;
    private final LibraryFineService fineService;
    private final RetryHelper retryHelper = new RetryHelper();

    @PostMapping("/borrowing/issue")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'LIBRARIAN')")
    public ResponseEntity<ApiResponse<LibraryViews.LoanView>> issueBook(
            @Valid @RequestBody LibraryRequests.IssueRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        // Retry wrapper runs OUTSIDE the @Transactional boundary on the service
        LibraryViews.LoanView view = retryHelper.execute(() ->
                borrowingService.issueBook(principal.getSchoolId(), request, principal));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Book issued", view));
    }

    @PostMapping("/borrowing/return")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'LIBRARIAN')")
    public ResponseEntity<ApiResponse<LibraryViews.ReturnResult>> returnBook(
            @Valid @RequestBody LibraryRequests.ReturnRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        LibraryViews.ReturnResult result = retryHelper.execute(() ->
                borrowingService.returnBook(principal.getSchoolId(), request, principal));
        return ResponseEntity.ok(ApiResponse.success("Book returned", result));
    }

    @PostMapping("/borrowing/loans/{loanId}/renew")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'LIBRARIAN')")
    public ResponseEntity<ApiResponse<LibraryViews.LoanView>> renewBook(
            @PathVariable Long loanId,
            @AuthenticationPrincipal UserPrincipal principal) {
        LibraryViews.LoanView view = retryHelper.execute(() ->
                borrowingService.renewBook(principal.getSchoolId(), loanId, principal));
        return ResponseEntity.ok(ApiResponse.success("Loan renewed", view));
    }

    @PostMapping("/borrowing/lost")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'LIBRARIAN')")
    public ResponseEntity<ApiResponse<LibraryViews.LostResult>> markLost(
            @RequestParam String bookNumber,
            @AuthenticationPrincipal UserPrincipal principal) {
        LibraryViews.LostResult result = retryHelper.execute(() ->
                borrowingService.markLost(principal.getSchoolId(), bookNumber, principal));
        return ResponseEntity.ok(ApiResponse.success("Book marked as lost", result));
    }

    // ==================== Fines ====================

    @PostMapping("/fines/{fineId}/assess")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LibraryViews.FineView>> assessFine(
            @PathVariable Long fineId,
            @Valid @RequestBody LibraryRequests.AssessFineRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        LibraryViews.FineView view = fineService.assessFine(principal.getSchoolId(), fineId,
                request.amount(), request.reason(), principal);
        return ResponseEntity.ok(ApiResponse.success("Fine assessed", view));
    }

    @PostMapping("/fines/{fineId}/waive")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LibraryViews.FineView>> waiveFine(
            @PathVariable Long fineId,
            @Valid @RequestBody LibraryRequests.WaiveFineRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        LibraryViews.FineView view = fineService.waiveFine(principal.getSchoolId(), fineId,
                request.reason(), principal);
        return ResponseEntity.ok(ApiResponse.success("Fine waived", view));
    }

    @PostMapping("/fines/{fineId}/pay")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LibraryViews.FineView>> payFine(
            @PathVariable Long fineId,
            @Valid @RequestBody LibraryRequests.PayFineRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        LibraryViews.FineView view = fineService.payFine(principal.getSchoolId(), fineId,
                request.reference(), principal);
        return ResponseEntity.ok(ApiResponse.success("Fine paid", view));
    }
}
