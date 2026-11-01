package com.peterscode.edutrack.library.controller;

import com.peterscode.edutrack.library.dto.LibraryRequests;
import com.peterscode.edutrack.library.dto.LibraryViews;
import com.peterscode.edutrack.library.service.LibraryCatalogueService;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.security.RequiresModule;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.ApiResponse;
import com.peterscode.edutrack.shared.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/library")
@RequiredArgsConstructor
@RequiresModule(ServiceModule.LIBRARY)
public class LibraryCatalogueController {

    private final LibraryCatalogueService catalogueService;

    // ==================== Categories ====================

    @PostMapping("/categories")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'LIBRARIAN')")
    public ResponseEntity<ApiResponse<LibraryViews.CategoryView>> createCategory(
            @Valid @RequestBody LibraryRequests.CreateCategoryRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        LibraryViews.CategoryView view = catalogueService.createCategory(principal.getSchoolId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created", view));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'LIBRARIAN')")
    public ResponseEntity<ApiResponse<LibraryViews.CategoryView>> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody LibraryRequests.UpdateCategoryRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        LibraryViews.CategoryView view = catalogueService.updateCategory(principal.getSchoolId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Category updated", view));
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'LIBRARIAN', 'TEACHER', 'STUDENT')")
    public ResponseEntity<ApiResponse<PageResponse<LibraryViews.CategoryView>>> listCategories(
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        Page<LibraryViews.CategoryView> page = catalogueService.listCategories(principal.getSchoolId(), pageable);
        return ResponseEntity.ok(ApiResponse.success("Categories", PageResponse.from(page)));
    }

    @GetMapping("/categories/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'LIBRARIAN', 'TEACHER', 'STUDENT')")
    public ResponseEntity<ApiResponse<LibraryViews.CategoryView>> getCategory(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        LibraryViews.CategoryView view = catalogueService.getCategory(principal.getSchoolId(), id);
        return ResponseEntity.ok(ApiResponse.success("Category", view));
    }

    // ==================== Books ====================

    @PostMapping("/books")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'LIBRARIAN')")
    public ResponseEntity<ApiResponse<LibraryViews.BookView>> createBook(
            @Valid @RequestBody LibraryRequests.CreateBookRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        LibraryViews.BookView view = catalogueService.createBook(principal.getSchoolId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Book created", view));
    }

    @PutMapping("/books/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'LIBRARIAN')")
    public ResponseEntity<ApiResponse<LibraryViews.BookView>> updateBook(
            @PathVariable Long id,
            @Valid @RequestBody LibraryRequests.UpdateBookRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        LibraryViews.BookView view = catalogueService.updateBook(principal.getSchoolId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Book updated", view));
    }

    @GetMapping("/books")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'LIBRARIAN', 'TEACHER', 'STUDENT')")
    public ResponseEntity<ApiResponse<PageResponse<LibraryViews.BookView>>> listBooks(
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        Page<LibraryViews.BookView> page = catalogueService.listBooks(principal.getSchoolId(), pageable);
        return ResponseEntity.ok(ApiResponse.success("Books", PageResponse.from(page)));
    }

    @GetMapping("/books/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'LIBRARIAN', 'TEACHER', 'STUDENT')")
    public ResponseEntity<ApiResponse<LibraryViews.BookView>> getBook(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        LibraryViews.BookView view = catalogueService.getBook(principal.getSchoolId(), id);
        return ResponseEntity.ok(ApiResponse.success("Book", view));
    }

    // ==================== Search ====================

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'LIBRARIAN', 'TEACHER', 'STUDENT')")
    public ResponseEntity<ApiResponse<PageResponse<LibraryViews.AvailableBookView>>> searchAvailableBooks(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String isbn,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        Page<LibraryViews.AvailableBookView> results = catalogueService.searchAvailableBooks(
                principal.getSchoolId(), categoryId, keyword, isbn, language, sortBy, sortDir, page, size);
        return ResponseEntity.ok(ApiResponse.success("Search results", PageResponse.from(results)));
    }

    // ==================== Copies ====================

    @PostMapping("/copies")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'LIBRARIAN')")
    public ResponseEntity<ApiResponse<LibraryViews.CopyView>> createCopy(
            @Valid @RequestBody LibraryRequests.CreateCopyRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        LibraryViews.CopyView view = catalogueService.createCopy(principal.getSchoolId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Copy created", view));
    }

    @PostMapping("/copies/batch")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'LIBRARIAN')")
    public ResponseEntity<ApiResponse<LibraryViews.BatchCopyResult>> batchCreateCopies(
            @Valid @RequestBody LibraryRequests.BatchCreateCopiesRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        LibraryViews.BatchCopyResult result = catalogueService.batchCreateCopies(principal.getSchoolId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Batch complete", result));
    }

    @GetMapping("/books/{bookId}/available-copies")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'LIBRARIAN')")
    public ResponseEntity<ApiResponse<List<LibraryViews.AvailableCopyView>>> listAvailableCopies(
            @PathVariable Long bookId,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<LibraryViews.AvailableCopyView> copies = catalogueService.listAvailableCopies(principal.getSchoolId(), bookId);
        return ResponseEntity.ok(ApiResponse.success("Available copies", copies));
    }

    @GetMapping("/copies/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'LIBRARIAN')")
    public ResponseEntity<ApiResponse<LibraryViews.CopyView>> getCopy(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        LibraryViews.CopyView view = catalogueService.getCopy(principal.getSchoolId(), id);
        return ResponseEntity.ok(ApiResponse.success("Copy", view));
    }
}
