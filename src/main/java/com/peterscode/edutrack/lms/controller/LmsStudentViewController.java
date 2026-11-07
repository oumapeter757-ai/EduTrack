package com.peterscode.edutrack.lms.controller;

import com.peterscode.edutrack.lms.dto.LmsViews;
import com.peterscode.edutrack.lms.entity.LmsCourse;
import com.peterscode.edutrack.lms.entity.LmsSubmission;
import com.peterscode.edutrack.lms.exception.LmsRuleException;
import com.peterscode.edutrack.lms.repository.LmsCourseRepository;
import com.peterscode.edutrack.lms.repository.LmsSubmissionRepository;
import com.peterscode.edutrack.lms.service.LmsAccessGuard;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.security.RequiresModule;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.ApiResponse;
import com.peterscode.edutrack.shared.response.PageResponse;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.enums.StudentStatus;
import com.peterscode.edutrack.academicyear.repository.AcademicYearRepository;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.timetable.entity.StreamSubject;
import com.peterscode.edutrack.timetable.repository.StreamSubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.peterscode.edutrack.student.repository.StudentRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static com.peterscode.edutrack.lms.controller.LmsParentViewController.getPageResponseResponseEntity;

@RestController
@RequestMapping("/api/v1/lms/student")
@RequiredArgsConstructor
@RequiresModule(ServiceModule.LMS)
public class LmsStudentViewController {

    private final LmsCourseRepository courseRepository;
    private final LmsSubmissionRepository submissionRepository;
    private final StudentRepository studentRepository;
    private final StreamSubjectRepository streamSubjectRepository;
    private final AcademicYearRepository academicYearRepository;
    private final LmsAccessGuard accessGuard;

    @GetMapping("/courses")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<List<LmsViews.CourseView>>> myCourses(
            @AuthenticationPrincipal UserPrincipal principal) {
        Optional<Student> student = studentRepository.findBySchoolIdAndUserId(
                principal.getSchoolId(), principal.getId());
        if (student.isEmpty() || student.get().getStatus() != StudentStatus.ACTIVE) {
            throw new LmsRuleException("ACCESS_DENIED", HttpStatus.NOT_FOUND, "No courses found");
        }

        Long streamId = student.get().getStreamId();
        Long schoolId = principal.getSchoolId();

        // Get the school's current academic year
        Long academicYearId = academicYearRepository.findBySchoolIdAndCurrentTrue(schoolId)
                .map(com.peterscode.edutrack.academicyear.entity.AcademicYear::getId)
                .orElse(null);

        // Find all stream-subjects for this student's stream
        List<StreamSubject> streamSubjects = academicYearId != null
                ? streamSubjectRepository.findBySchoolIdAndStreamIdAndAcademicYearId(schoolId, streamId, academicYearId)
                : List.of();

        // Find courses for these stream-subjects
        List<LmsCourse> courses = courseRepository.findBySchoolId(schoolId, Pageable.unpaged()).getContent().stream()
                .filter(c -> streamSubjects.stream().anyMatch(ss -> ss.getId().equals(c.getStreamSubjectId())))
                .toList();

        List<LmsViews.CourseView> views = courses.stream().map(c ->
                new LmsViews.CourseView(
                        c.getId(), c.getTitle(), c.getStatus().name(),
                        c.getStreamSubjectId(), c.getCreatedBy(),
                        c.getCreatedAt() != null ? c.getCreatedAt().toString() : null
                )
        ).toList();

        return ResponseEntity.ok(ApiResponse.success("My courses", views));
    }

    @GetMapping("/assignments")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<List<Object>>> myAssignments(
            @AuthenticationPrincipal UserPrincipal principal) {
        // Simplified - returns empty list
        // A full implementation would join assignments with submissions
        return ResponseEntity.ok(ApiResponse.success("My assignments", List.of()));
    }

    @GetMapping("/scores")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<PageResponse<LmsViews.SubmissionView>> myReleasedScores(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long studentId = studentRepository.findBySchoolIdAndUserId(principal.getSchoolId(), principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"))
                .getId();
        Page<LmsSubmission> submissions = submissionRepository.findBySchoolIdAndStudentId(
                principal.getSchoolId(), studentId, pageable);
        return getPageResponseResponseEntity(pageable, submissions);
    }
}
