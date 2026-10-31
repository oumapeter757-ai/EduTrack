package com.peterscode.edutrack.timetable.controller;

import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.security.RequiresModule;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.ApiResponse;
import com.peterscode.edutrack.timetable.dto.TimetableRequests.AssignmentBatch;
import com.peterscode.edutrack.timetable.dto.TimetableRequests.ApplyCurriculumRequest;
import com.peterscode.edutrack.timetable.dto.TimetableRequests.CurriculumRequest;
import com.peterscode.edutrack.timetable.dto.TimetableViews.AssignmentView;
import com.peterscode.edutrack.timetable.dto.TimetableViews.CurriculumView;
import com.peterscode.edutrack.timetable.service.TimetableService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StreamSubjectController {
    private final TimetableService service;

    @PutMapping("/class-levels/{classLevelId}/curriculum/template")
    @RequiresModule(ServiceModule.TIMETABLE)
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<List<CurriculumView>>> saveCurriculum(@PathVariable Long classLevelId,
            @Valid @RequestBody CurriculumRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Curriculum saved", service.saveCurriculum(request, principal)));
    }

    @GetMapping("/class-levels/{classLevelId}/curriculum/template")
    @RequiresModule(ServiceModule.TIMETABLE)
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<List<CurriculumView>>> curriculum(@PathVariable Long classLevelId,
            @RequestParam(required = false) Long schoolId, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Curriculum retrieved", service.curriculum(classLevelId, schoolId, principal)));
    }

    @PostMapping("/class-levels/{classLevelId}/curriculum/apply")
    @RequiresModule(ServiceModule.TIMETABLE)
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<List<AssignmentView>>> apply(@PathVariable Long classLevelId,
            @Valid @RequestBody ApplyCurriculumRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Curriculum applied to streams",
                service.applyCurriculum(classLevelId, request, principal)));
    }

    @PostMapping("/stream-subjects/batch")
    @RequiresModule(ServiceModule.TEACHER_MANAGEMENT)
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<List<AssignmentView>>> assign(@Valid @RequestBody AssignmentBatch request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Assignments saved", service.assign(request, principal)));
    }

    @GetMapping("/stream-subjects")
    @RequiresModule(ServiceModule.TEACHER_MANAGEMENT)
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<List<AssignmentView>>> assignments(@RequestParam Long academicYearId,
            @RequestParam(required = false) Long streamId, @RequestParam(required = false) Long schoolId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Assignments retrieved",
                service.assignments(streamId, academicYearId, schoolId, principal)));
    }
}
