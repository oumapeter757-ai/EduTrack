package com.peterscode.edutrack.guardian.controller;

import com.peterscode.edutrack.guardian.dto.*;
import com.peterscode.edutrack.guardian.service.GuardianService;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.peterscode.edutrack.security.NoModuleRequired;

@RestController
@NoModuleRequired
@RequestMapping("/api/v1/guardians")
@RequiredArgsConstructor
public class GuardianController {
  private final GuardianService service;

  @PostMapping
  @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
  public ResponseEntity<ApiResponse<GuardianResponseDto>> create(
      @Valid @RequestBody CreateGuardianRequestDto d, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success("Guardian created successfully", service.create(d, p)));
  }

  @GetMapping
  @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
  public ResponseEntity<ApiResponse<PageResponse<GuardianResponseDto>>> list(
      @RequestParam(required = false) Long schoolId,
      Pageable page,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(
        ApiResponse.success("Guardians retrieved successfully", service.list(schoolId, page, p)));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
  public ResponseEntity<ApiResponse<GuardianResponseDto>> get(
      @PathVariable Long id, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(
        ApiResponse.success("Guardian retrieved successfully", service.get(id, p)));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
  public ResponseEntity<ApiResponse<GuardianResponseDto>> update(
      @PathVariable Long id,
      @Valid @RequestBody UpdateGuardianRequestDto d,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(
        ApiResponse.success("Guardian updated successfully", service.update(id, d, p)));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
  public ResponseEntity<ApiResponse<Void>> delete(
      @PathVariable Long id, @AuthenticationPrincipal UserPrincipal p) {
    service.delete(id, p);
    return ResponseEntity.ok(ApiResponse.success("Guardian deleted successfully", null));
  }
}
