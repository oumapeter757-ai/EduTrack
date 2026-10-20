package com.peterscode.edutrack.student.controller;

import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.ApiResponse;
import com.peterscode.edutrack.shared.response.PageResponse;
import com.peterscode.edutrack.student.dto.*;
import com.peterscode.edutrack.student.enums.BloodGroup;
import com.peterscode.edutrack.student.enums.Gender;
import com.peterscode.edutrack.student.enums.StudentStatus;
import com.peterscode.edutrack.student.service.StudentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.peterscode.edutrack.security.RequiresModule;
import com.peterscode.edutrack.school.enums.ServiceModule;

@RestController
@RequiresModule(ServiceModule.STUDENT_MANAGEMENT)
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    /**
     * Create a new student (SUPER_ADMIN or SCHOOL_ADMIN of their own school).
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<StudentResponseDto>> createStudent(
            @Valid @RequestBody CreateStudentRequestDto requestDto,
            @AuthenticationPrincipal UserPrincipal principal) {
        StudentResponseDto response = studentService.createStudent(requestDto, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Student created successfully", response));
    }

    /**
     * Get student by ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAnyRole('SCHOOL_ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<ApiResponse<StudentResponseDto>> getStudentById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        StudentResponseDto response = studentService.getStudentById(id, principal);
        return ResponseEntity.ok(ApiResponse.success("Student retrieved successfully", response));
    }

    /**
     * Get student by unique admission number.
     */
    @GetMapping("/admission/{admissionNumber}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<StudentResponseDto>> getStudentByAdmissionNumber(
            @PathVariable String admissionNumber,
            @RequestParam(required = false) Long schoolId,
            @AuthenticationPrincipal UserPrincipal principal) {
        StudentResponseDto response = studentService.getStudentByAdmissionNumber(admissionNumber, schoolId, principal);
        return ResponseEntity.ok(ApiResponse.success("Student retrieved successfully", response));
    }

    /**
     * Get student profile for current authenticated student user.
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<StudentResponseDto>> getCurrentStudent(
            @AuthenticationPrincipal UserPrincipal principal) {
        StudentResponseDto response = studentService.getCurrentStudentProfile(principal);
        return ResponseEntity.ok(ApiResponse.success("Current student profile retrieved successfully", response));
    }

    /**
     * Update student details.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<StudentResponseDto>> updateStudent(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStudentRequestDto requestDto,
            @AuthenticationPrincipal UserPrincipal principal) {
        StudentResponseDto response = studentService.updateStudent(id, requestDto, principal);
        return ResponseEntity.ok(ApiResponse.success("Student updated successfully", response));
    }

    /**
     * Update student status (e.g. ACTIVE, SUSPENDED, GRADUATED, etc.).
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<StudentResponseDto>> updateStudentStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStudentStatusRequestDto requestDto,
            @AuthenticationPrincipal UserPrincipal principal) {
        StudentResponseDto response = studentService.updateStudentStatus(id, requestDto, principal);
        return ResponseEntity.ok(ApiResponse.success("Student status updated successfully", response));
    }

    /**
     * Bulk update status for multiple students.
     */
    @PostMapping("/bulk-status")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> bulkUpdateStatus(
            @Valid @RequestBody BulkStudentStatusUpdateRequestDto requestDto,
            @AuthenticationPrincipal UserPrincipal principal) {
        studentService.bulkUpdateStatus(requestDto, principal);
        return ResponseEntity.ok(ApiResponse.success("Student statuses updated successfully", null));
    }

    /**
     * Soft delete / withdraw a student.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteStudent(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        studentService.deleteStudent(id, principal);
        return ResponseEntity.ok(ApiResponse.success("Student deactivated successfully", null));
    }

    /**
     * Search and filter students with pagination.
     */
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<PageResponse<StudentSummaryDto>>> searchStudents(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) StudentStatus status,
            @RequestParam(required = false) Gender gender,
            @RequestParam(required = false) BloodGroup bloodGroup,
            @RequestParam(required = false) Boolean active,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        StudentSearchCriteria criteria = StudentSearchCriteria.builder()
                .search(search)
                .schoolId(schoolId)
                .gradeLevel(gradeLevel)
                .section(section)
                .academicYear(academicYear)
                .status(status)
                .gender(gender)
                .bloodGroup(bloodGroup)
                .active(active)
                .build();

        PageResponse<StudentSummaryDto> response = studentService.searchStudents(criteria, pageable, principal);
        return ResponseEntity.ok(ApiResponse.success("Students retrieved successfully", response));
    }

    /**
     * Get student statistics and capacity utilization.
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<StudentStatsDto>> getStudentStatistics(
            @RequestParam(required = false) Long schoolId,
            @AuthenticationPrincipal UserPrincipal principal) {
        StudentStatsDto response = studentService.getStudentStatistics(schoolId, principal);
        return ResponseEntity.ok(ApiResponse.success("Student statistics retrieved successfully", response));
    }

    /**
     * Link student profile to a user login account.
     */
    @PostMapping("/{id}/link-user/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<StudentResponseDto>> linkUserAccount(
            @PathVariable Long id,
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        StudentResponseDto response = studentService.linkUserAccount(id, userId, principal);
        return ResponseEntity.ok(ApiResponse.success("User linked to student successfully", response));
    }

    /**
     * Unlink student profile from user login account.
     */
    @PostMapping("/{id}/unlink-user")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<StudentResponseDto>> unlinkUserAccount(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        StudentResponseDto response = studentService.unlinkUserAccount(id, principal);
        return ResponseEntity.ok(ApiResponse.success("User unlinked from student successfully", response));
    }
}
