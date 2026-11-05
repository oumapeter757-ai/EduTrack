package com.peterscode.edutrack.enrollment.controller;

import com.peterscode.edutrack.enrollment.dto.PromotionRequests.CreateRun;
import com.peterscode.edutrack.enrollment.dto.PromotionRequests.OverrideBatch;
import com.peterscode.edutrack.enrollment.entity.PromotionDecision;
import com.peterscode.edutrack.enrollment.entity.PromotionRun;
import com.peterscode.edutrack.enrollment.service.PromotionRunService;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.security.*;
import com.peterscode.edutrack.shared.response.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequiresModule(ServiceModule.STUDENT_MANAGEMENT)
@RequestMapping("/api/v1/enrollment/promotion-runs")
public class PromotionRunController {
    private final PromotionRunService service;

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<PromotionRun>> create(@Valid @RequestBody CreateRun request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Promotion run created", service.create(request, principal)));
    }

    @PostMapping("/{id}/preview")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<PromotionRun>> preview(@PathVariable Long id,
            @RequestParam Long schoolId, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Promotion preview generated", service.preview(id, schoolId, principal)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<PromotionRun>> get(@PathVariable Long id, @RequestParam Long schoolId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Promotion run retrieved", service.get(id, schoolId, principal)));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<PromotionRun>>> list(@RequestParam Long schoolId, Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Promotion runs retrieved", service.list(schoolId, pageable, principal)));
    }

    @GetMapping("/{id}/decisions")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<PromotionDecision>>> decisions(@PathVariable Long id,
            @RequestParam Long schoolId, Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Promotion decisions retrieved", service.decisions(id, schoolId, pageable, principal)));
    }

    @PutMapping("/{id}/decisions")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> override(@PathVariable Long id, @RequestParam Long schoolId,
            @Valid @RequestBody OverrideBatch batch, @AuthenticationPrincipal UserPrincipal principal) {
        service.override(id, schoolId, batch, principal);
        return ResponseEntity.ok(ApiResponse.success("Promotion decisions updated", null));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<PromotionRun>> approve(@PathVariable Long id, @RequestParam Long schoolId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Promotion run approved", service.approve(id, schoolId, principal)));
    }

    @PostMapping("/{id}/cancel-approval")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<PromotionRun>> cancelApproval(@PathVariable Long id, @RequestParam Long schoolId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Promotion approval cancelled", service.cancelApproval(id, schoolId, principal)));
    }

    @PostMapping("/{id}/execute")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Long>> execute(@PathVariable Long id, @RequestParam Long schoolId,
            @AuthenticationPrincipal UserPrincipal principal) {
        service.execute(id, schoolId, principal);
        return ResponseEntity.accepted().body(ApiResponse.success("Promotion execution queued", id));
    }

    @GetMapping("/{id}/progress")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<PromotionRun>> progress(@PathVariable Long id, @RequestParam Long schoolId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Promotion progress retrieved", service.progress(id, schoolId, principal)));
    }

    @PostMapping("/{id}/revert")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<PromotionRun>> revert(@PathVariable Long id, @RequestParam Long schoolId,
            @RequestParam String reason, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.accepted().body(ApiResponse.success("Promotion revert completed",
                service.revert(id, schoolId, reason, principal)));
    }
}
