package com.peterscode.edutrack.grading.controller;

import com.peterscode.edutrack.grading.dto.GradingRequests;
import com.peterscode.edutrack.grading.dto.GradingViews;
import com.peterscode.edutrack.grading.service.GradingService;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.security.RequiresModule;
import com.peterscode.edutrack.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/grading")
@RequiredArgsConstructor
@RequiresModule(ServiceModule.GRADING)
public class GradingController {

    private final GradingService gradingService;

    // ==================== Grade Scale ====================

    @PostMapping("/scales")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<GradingViews.GradeScaleView> createGradeScale(
            @RequestParam Long schoolId,
            @Valid @RequestBody GradingRequests.CreateGradeScaleRequest request,
            UserPrincipal principal) {
        return ResponseEntity.ok(gradingService.createGradeScale(schoolId, request, principal));
    }

    @GetMapping("/scales")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<GradingViews.GradeScaleView>> gradeScales(
            @RequestParam Long schoolId,
            UserPrincipal principal) {
        return ResponseEntity.ok(gradingService.gradeScales(schoolId, principal));
    }

    @PutMapping("/scales/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<GradingViews.GradeScaleView> updateGradeScale(
            @RequestParam Long schoolId,
            @PathVariable Long id,
            @Valid @RequestBody GradingRequests.CreateGradeScaleRequest request,
            UserPrincipal principal) {
        return ResponseEntity.ok(gradingService.updateGradeScale(schoolId, id, request, principal));
    }

    // ==================== Assessment Type ====================

    @PostMapping("/assessment-types")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<GradingViews.AssessmentTypeView> createAssessmentType(
            @RequestParam Long schoolId,
            @Valid @RequestBody GradingRequests.CreateAssessmentTypeRequest request,
            UserPrincipal principal) {
        return ResponseEntity.ok(gradingService.createAssessmentType(schoolId, request, principal));
    }

    @GetMapping("/assessment-types")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<GradingViews.AssessmentTypeView>> assessmentTypes(
            @RequestParam Long schoolId,
            UserPrincipal principal) {
        return ResponseEntity.ok(gradingService.assessmentTypes(schoolId, principal));
    }

    @PostMapping("/assessment-types/{id}/papers")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<GradingViews.AssessmentPaperView> addAssessmentPaper(
            @RequestParam Long schoolId,
            @PathVariable Long id,
            @Valid @RequestBody GradingRequests.CreateAssessmentPaperRequest request,
            UserPrincipal principal) {
        return ResponseEntity.ok(gradingService.addAssessmentPaper(schoolId, id, request, principal));
    }

    @GetMapping("/assessment-types/{id}/papers")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<GradingViews.AssessmentPaperView>> assessmentPapers(
            @RequestParam Long schoolId,
            @PathVariable Long id,
            UserPrincipal principal) {
        return ResponseEntity.ok(gradingService.assessmentPapers(schoolId, id, principal));
    }

    // ==================== Subject Assessment ====================

    @PostMapping("/subjects/configure")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> configureSubjectAssessments(
            @RequestParam Long schoolId,
            @RequestParam Long termId,
            @Valid @RequestBody GradingRequests.ConfigureSubjectAssessmentsRequest request,
            UserPrincipal principal) {
        gradingService.configureSubjectAssessments(schoolId, termId, request, principal);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/subjects/{subjectId}/assessments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<GradingViews.SubjectAssessmentView>> subjectAssessments(
            @RequestParam Long schoolId,
            @RequestParam Long termId,
            @PathVariable Long subjectId,
            UserPrincipal principal) {
        return ResponseEntity.ok(gradingService.subjectAssessments(schoolId, termId, subjectId, principal));
    }

    // ==================== Score Entry ====================

    @PostMapping("/scores/submit")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<GradingViews.SubmitScoresResponse> submitScores(
            @RequestParam Long schoolId,
            @Valid @RequestBody GradingRequests.SubmitScoresRequest request,
            UserPrincipal principal) {
        return ResponseEntity.ok(gradingService.submitScores(schoolId, request, principal));
    }

    @GetMapping("/scores/stream")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<GradingViews.StudentScoreView>> streamScores(
            @RequestParam Long schoolId,
            @RequestParam Long termId,
            @RequestParam Long streamId,
            @RequestParam Long subjectId,
            UserPrincipal principal) {
        return ResponseEntity.ok(gradingService.streamScores(schoolId, termId, streamId, subjectId, principal));
    }

    @GetMapping("/scores/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<GradingViews.MyScoreView>> myScores(
            @RequestParam Long schoolId,
            @RequestParam Long termId,
            UserPrincipal principal) {
        return ResponseEntity.ok(gradingService.myScores(schoolId, principal.getId(), termId, principal));
    }

    // ==================== Term Grade ====================

    @PostMapping("/terms/{id}/close")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> closeTerm(
            @RequestParam Long schoolId,
            @PathVariable Long id,
            UserPrincipal principal) {
        gradingService.closeTerm(schoolId, id, principal);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/terms/{id}/grades/stream")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<GradingViews.TermGradeView>> streamTermGrades(
            @RequestParam Long schoolId,
            @PathVariable Long id,
            @RequestParam Long streamId,
            @RequestParam Long subjectId,
            UserPrincipal principal) {
        return ResponseEntity.ok(gradingService.streamTermGrades(schoolId, id, streamId, subjectId, principal));
    }

    @GetMapping("/terms/{id}/grades/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<GradingViews.MyTermGradeView>> myTermGrades(
            @RequestParam Long schoolId,
            @PathVariable Long id,
            UserPrincipal principal) {
        return ResponseEntity.ok(gradingService.myTermGrades(schoolId, principal.getId(), id, principal));
    }

    // ==================== Statistics ====================

    @GetMapping("/statistics/class")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<GradingViews.ClassStatisticsView> classStatistics(
            @RequestParam Long schoolId,
            @RequestParam Long termId,
            @RequestParam Long streamId,
            @RequestParam Long subjectId,
            UserPrincipal principal) {
        return ResponseEntity.ok(gradingService.classStatistics(schoolId, termId, streamId, subjectId, principal));
    }

    @GetMapping("/report-cards/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<GradingViews.ReportCardView> myReportCard(
            @RequestParam Long schoolId,
            @RequestParam Long termId,
            UserPrincipal principal) {
        return ResponseEntity.ok(gradingService.reportCard(schoolId, termId, principal.getId(), principal));
    }

    @GetMapping("/report-cards/student/{studentId}")
    @PreAuthorize("hasAnyRole('PARENT', 'SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<GradingViews.ReportCardView> reportCardByStudentId(
            @RequestParam Long schoolId,
            @RequestParam Long termId,
            @PathVariable Long studentId,
            UserPrincipal principal) {
        return ResponseEntity.ok(gradingService.reportCard(schoolId, termId, studentId, principal));
    }

    @GetMapping("/students/{studentId}/term-grades")
    @PreAuthorize("hasAnyRole('PARENT', 'STUDENT', 'TEACHER', 'SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<GradingViews.TermGradeView>> studentTermGrades(
            @RequestParam Long schoolId,
            @RequestParam Long termId,
            @PathVariable Long studentId,
            UserPrincipal principal) {
        return ResponseEntity.ok(gradingService.studentTermGrades(schoolId, termId, studentId, principal));
    }
}
