package com.peterscode.edutrack.staff.controller;

import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.*;
import com.peterscode.edutrack.staff.dto.*;
import com.peterscode.edutrack.staff.service.StaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.peterscode.edutrack.security.RequiresModule;
import com.peterscode.edutrack.school.enums.ServiceModule;

@RestController
@RequiresModule(ServiceModule.TEACHER_MANAGEMENT)
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
public class StaffController {
  private final StaffService service;

  @PostMapping
  @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
  public ResponseEntity<ApiResponse<StaffResponseDto>> create(
      @Valid @RequestBody CreateStaffRequestDto d, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success("Staff created successfully", service.create(d, p)));
  }

  @GetMapping
  @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
  public ResponseEntity<ApiResponse<PageResponse<StaffResponseDto>>> list(
      @RequestParam(required = false) Long schoolId,
      Pageable page,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(
        ApiResponse.success("Staff retrieved successfully", service.list(schoolId, page, p)));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN') or hasRole('TEACHER')")
  public ResponseEntity<ApiResponse<StaffResponseDto>> get(
      @PathVariable Long id, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(
        ApiResponse.success("Staff retrieved successfully", service.get(id, p)));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
  public ResponseEntity<ApiResponse<StaffResponseDto>> update(
      @PathVariable Long id,
      @Valid @RequestBody UpdateStaffRequestDto d,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(
        ApiResponse.success("Staff updated successfully", service.update(id, d, p)));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
  public ResponseEntity<ApiResponse<Void>> delete(
      @PathVariable Long id, @AuthenticationPrincipal UserPrincipal p) {
    service.delete(id, p);
    return ResponseEntity.ok(ApiResponse.success("Staff deleted successfully", null));
  }
}
