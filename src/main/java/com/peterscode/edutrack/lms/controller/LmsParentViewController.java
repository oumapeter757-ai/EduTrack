package com.peterscode.edutrack.lms.controller;

import com.peterscode.edutrack.lms.dto.LmsViews;
import com.peterscode.edutrack.lms.entity.LmsCourse;
import com.peterscode.edutrack.lms.entity.LmsSubmission;
import com.peterscode.edutrack.lms.exception.LmsRuleException;
import com.peterscode.edutrack.lms.repository.LmsCourseRepository;
import com.peterscode.edutrack.lms.repository.LmsSubmissionRepository;
import com.peterscode.edutrack.guardian.repository.GuardianRepository;
import com.peterscode.edutrack.guardian.repository.ParentStudentMappingRepository;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.security.ParentAccessGuard;
import com.peterscode.edutrack.security.RequiresModule;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.ApiResponse;
import com.peterscode.edutrack.shared.response.PageResponse;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.enums.StudentStatus;
import com.peterscode.edutrack.student.repository.StudentRepository;
import com.peterscode.edutrack.timetable.entity.StreamSubject;
import com.peterscode.edutrack.timetable.repository.StreamSubjectRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/lms/parent")
@RequiredArgsConstructor
@RequiresModule(ServiceModule.LMS)
public class LmsParentViewController {

    private final LmsCourseRepository courseRepository;
    private final LmsSubmissionRepository submissionRepository;
    private final StudentRepository studentRepository;
    private final StreamSubjectRepository streamSubjectRepository;
    private final ParentStudentMappingRepository parentStudentMappingRepository;
    private final GuardianRepository guardianRepository;

    @GetMapping("/children/{childId}/courses")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ApiResponse<List<LmsViews.CourseView>>> childCourses(
            @PathVariable Long childId,
            @AuthenticationPrincipal UserPrincipal principal) {
        // Verify parent-child link
        ParentAccessGuard.assertLinked(principal.getSchoolId(), principal,
                parentStudentMappingRepository, guardianRepository, childId);

        Optional<Student> child = studentRepository.findById(childId);
        if (child.isEmpty() || child.get().getStatus() != StudentStatus.ACTIVE) {
            throw new LmsRuleException("CHILD_NOT_FOUND", HttpStatus.NOT_FOUND, "Child not found");
        }

        Long streamId = child.get().getStreamId();
        Long schoolId = principal.getSchoolId();

        // Get stream subjects for the child's stream
        List<StreamSubject> streamSubjects = streamSubjectRepository
                .findBySchoolIdAndStreamIdAndAcademicYearId(schoolId, streamId,
                        child.get().getAcademicYearId());

        List<LmsCourse> courses = courseRepository.findAll().stream()
                .filter(c -> c.getSchoolId().equals(schoolId))
                .filter(c -> streamSubjects.stream().anyMatch(ss -> ss.getId().equals(c.getStreamSubjectId())))
                .toList();

        List<LmsViews.CourseView> views = courses.stream().map(c ->
                new LmsViews.CourseView(
                        c.getId(), c.getTitle(), c.getStatus().name(),
                        c.getStreamSubjectId(), c.getCreatedBy(),
                        c.getCreatedAt() != null ? c.getCreatedAt().toString() : null
                )
        ).toList();

        return ResponseEntity.ok(ApiResponse.success("Child courses", views));
    }

    @GetMapping("/children/{childId}/assignments")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ApiResponse<List<Object>>> childAssignments(
            @PathVariable Long childId,
            @AuthenticationPrincipal UserPrincipal principal) {
        ParentAccessGuard.assertLinked(principal.getSchoolId(), principal,
                parentStudentMappingRepository, guardianRepository, childId);
        return ResponseEntity.ok(ApiResponse.success("Child assignments", List.of()));
    }

    @GetMapping("/children/{childId}/scores")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<PageResponse<LmsViews.SubmissionView>> childReleasedScores(
            @PathVariable Long childId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        ParentAccessGuard.assertLinked(principal.getSchoolId(), principal,
                parentStudentMappingRepository, guardianRepository, childId);

        Page<LmsSubmission> submissions = submissionRepository.findBySchoolIdAndStudentId(
                principal.getSchoolId(), childId, pageable);
        return getPageResponseResponseEntity(pageable, submissions);
    }

    @NonNull
    static ResponseEntity<PageResponse<LmsViews.SubmissionView>> getPageResponseResponseEntity(@PageableDefault(size = 20) Pageable pageable, Page<LmsSubmission> submissions) {
        List<LmsSubmission> released = submissions.getContent().stream()
                .filter(s -> s.getReleased() && s.getScore() != null)
                .toList();

        Page<LmsSubmission> releasedPage = new PageImpl<>(released, pageable, submissions.getTotalElements());

        var page = releasedPage.map(s ->
                new LmsViews.SubmissionView(
                        s.getId(), s.getAssignmentId(), null,
                        s.getSubmittedAt() != null ? s.getSubmittedAt().toString() : null,
                        s.getLate(), s.getAttemptCount(), s.getStatus().name(),
                        s.getScore() != null ? s.getScore().toString() : null,
                        s.getFeedback(), s.getReleased()
                )
        );

        return ResponseEntity.ok(PageResponse.from(page));
    }
}
