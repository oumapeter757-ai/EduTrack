package com.peterscode.edutrack.examination.controller;

import com.peterscode.edutrack.examination.dto.*;
import com.peterscode.edutrack.examination.service.ExaminationService;
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
@RequestMapping("/api/v1/exams")
@RequiredArgsConstructor
@RequiresModule(ServiceModule.EXAMINATION)
public class ExaminationController {

    private final ExaminationService examinationService;

    // ==================== Exam Series ====================

    @PostMapping("/series")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ExamSeriesView> createExamSeries(
            @RequestParam Long schoolId,
            @Valid @RequestBody CreateExamSeriesRequest request,
            UserPrincipal principal) {
        return ResponseEntity.ok(examinationService.createExamSeries(schoolId, request, principal));
    }

    @GetMapping("/series")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<ExamSeriesView>> examSeries(
            @RequestParam Long schoolId,
            UserPrincipal principal) {
        return ResponseEntity.ok(examinationService.examSeries(schoolId, principal));
    }

    @GetMapping("/series/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<ExamSeriesView> getExamSeries(
            @RequestParam Long schoolId,
            @PathVariable Long id,
            UserPrincipal principal) {
        return ResponseEntity.ok(examinationService.getExamSeries(schoolId, id, principal));
    }

    @PutMapping("/series/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ExamSeriesView> updateExamSeries(
            @RequestParam Long schoolId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateExamSeriesRequest request,
            UserPrincipal principal) {
        return ResponseEntity.ok(examinationService.updateExamSeries(schoolId, id, request, principal));
    }

    @PatchMapping("/series/{id}/status")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ExamSeriesView> updateStatus(
            @RequestParam Long schoolId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request,
            UserPrincipal principal) {
        return ResponseEntity.ok(examinationService.updateStatus(schoolId, id, request, principal));
    }

    // ==================== Registration ====================

    @PostMapping("/registrations")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'TEACHER')")
    public ResponseEntity<ExamRegistrationView> registerStudent(
            @RequestParam Long schoolId,
            @RequestParam Long seriesId,
            @Valid @RequestBody RegisterStudentRequest request,
            UserPrincipal principal) {
        return ResponseEntity.ok(examinationService.registerStudent(schoolId, seriesId, request, principal));
    }

    @PostMapping("/registrations/bulk")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<BulkRegisterResult> bulkRegister(
            @RequestParam Long schoolId,
            @RequestParam Long seriesId,
            @Valid @RequestBody BulkRegisterRequest request,
            UserPrincipal principal) {
        return ResponseEntity.ok(examinationService.bulkRegister(schoolId, seriesId, request, principal));
    }

    @PutMapping("/registrations/{id}/withdraw")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ExamRegistrationView> withdrawRegistration(
            @RequestParam Long schoolId,
            @PathVariable Long id,
            @Valid @RequestBody WithdrawRequest request,
            UserPrincipal principal) {
        return ResponseEntity.ok(examinationService.withdrawRegistration(schoolId, id, request, principal));
    }

    @PutMapping("/registrations/{id}/debar")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ExamRegistrationView> debarRegistration(
            @RequestParam Long schoolId,
            @PathVariable Long id,
            @Valid @RequestBody DebarRequest request,
            UserPrincipal principal) {
        return ResponseEntity.ok(examinationService.debarRegistration(schoolId, id, request, principal));
    }

    // ==================== Views ====================

    @GetMapping("/series/{id}/registrations")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<ExamRegistrationView>> seriesRegistrations(
            @RequestParam Long schoolId,
            @PathVariable Long id,
            UserPrincipal principal) {
        return ResponseEntity.ok(examinationService.seriesRegistrations(schoolId, id, principal));
    }

    @GetMapping("/registrations/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<MyRegistrationView>> myRegistrations(
            @RequestParam Long schoolId,
            UserPrincipal principal) {
        return ResponseEntity.ok(examinationService.myRegistrations(schoolId, principal));
    }
}
