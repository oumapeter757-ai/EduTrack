package com.peterscode.edutrack.enrollment.controller;

import com.peterscode.edutrack.enrollment.entity.EnrollmentStatus;
import com.peterscode.edutrack.enrollment.repository.StudentEnrollmentRepository;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.security.*;
import com.peterscode.edutrack.shared.response.*;
import com.peterscode.edutrack.timetable.repository.StreamSubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.peterscode.edutrack.shared.PageableSortValidator;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequiresModule(ServiceModule.STUDENT_MANAGEMENT)
@RequestMapping("/api/v1/enrollment/rosters")
public class EnrollmentRosterController {
    private final StudentEnrollmentRepository enrollments;
    private final StreamSubjectRepository assignments;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAnyRole('SCHOOL_ADMIN','TEACHER')")
    public ResponseEntity<ApiResponse<PageResponse<com.peterscode.edutrack.enrollment.dto.EnrollmentView>>> roster(
            @RequestParam Long schoolId, @RequestParam Long academicYearId,
            @RequestParam Long classLevelId, @RequestParam Long streamId, Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal.getRole() != com.peterscode.edutrack.user.entity.Role.SUPER_ADMIN
                && !schoolId.equals(principal.getSchoolId())) throw new org.springframework.security.access.AccessDeniedException("Access denied");
        if (principal.getRole() == com.peterscode.edutrack.user.entity.Role.TEACHER
                && !assignments.findAssignedStreamIds(schoolId, principal.getId(), academicYearId).contains(streamId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }
        Pageable safePageable = PageableSortValidator.validate(pageable,
                Set.of("id", "studentId", "academicYearId", "classLevelId", "streamId", "startDate"));
        return ResponseEntity.ok(ApiResponse.success("Roster retrieved",
                PageResponse.from(enrollments.findBySchoolIdAndAcademicYearIdAndClassLevelIdAndStreamIdAndStatus(
                        schoolId, academicYearId, classLevelId, streamId, EnrollmentStatus.ENROLLED, safePageable)
                        .map(e -> com.peterscode.edutrack.enrollment.dto.EnrollmentView.builder()
                                .id(e.getId()).schoolId(e.getSchoolId()).studentId(e.getStudentId())
                                .academicYearId(e.getAcademicYearId()).classLevelId(e.getClassLevelId())
                                .streamId(e.getStreamId()).status(e.getStatus()).startDate(e.getStartDate())
                                .endDate(e.getEndDate()).outcomeReason(e.getOutcomeReason()).source(e.getSource()).build()))));
    }
}
