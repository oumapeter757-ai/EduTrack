package com.peterscode.edutrack.timetable.controller;

import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.security.RequiresModule;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.ApiResponse;
import com.peterscode.edutrack.timetable.dto.TimetableRequests.*;
import com.peterscode.edutrack.timetable.dto.TimetableViews.*;
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
@RequiresModule(ServiceModule.TIMETABLE)
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TimetableController {
    private final TimetableService service;

    @GetMapping("/periods")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<List<PeriodView>>> periods(@RequestParam(required = false) Long schoolId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Periods retrieved", service.periods(schoolId, principal)));
    }

    @PostMapping("/periods")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<PeriodView>> createPeriod(@Valid @RequestBody PeriodRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Period created", service.savePeriod(null, request, principal)));
    }

    @PutMapping("/periods/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<PeriodView>> updatePeriod(@PathVariable Long id, @Valid @RequestBody PeriodRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Period updated", service.savePeriod(id, request, principal)));
    }

    @GetMapping("/rooms")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<List<RoomView>>> rooms(@RequestParam(required = false) Long schoolId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Rooms retrieved", service.rooms(schoolId, principal)));
    }

    @PostMapping("/rooms")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<RoomView>> createRoom(@Valid @RequestBody RoomRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Room created", service.saveRoom(null, request, principal)));
    }

    @PutMapping("/rooms/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<RoomView>> updateRoom(@PathVariable Long id, @Valid @RequestBody RoomRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Room updated", service.saveRoom(id, request, principal)));
    }

    @PostMapping("/timetables")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<TimetableView>> create(@Valid @RequestBody TimetableRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Timetable created", service.createTimetable(request, principal)));
    }

    @GetMapping("/timetables/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<TimetableView>> get(@PathVariable Long id, @RequestParam(required = false) Long schoolId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Timetable retrieved", service.timetable(id, schoolId, principal)));
    }

    @GetMapping("/timetables/{id}/completeness")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<CompletenessView>> completeness(@PathVariable Long id,
            @RequestParam(required = false) Long schoolId, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Completeness retrieved", service.completeness(id, schoolId, principal)));
    }

    @GetMapping("/timetables/{id}/streams/{streamId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<List<LessonView>>> streamLessons(@PathVariable Long id, @PathVariable Long streamId,
            @RequestParam(required = false) Long schoolId, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Stream timetable retrieved", service.streamLessons(id, streamId, schoolId, principal)));
    }

    @PostMapping("/timetables/{id}/lessons/batch")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<LessonBatchView>> lessons(@PathVariable Long id, @Valid @RequestBody LessonBatch request,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (!id.equals(request.timetableId())) {
            throw new IllegalArgumentException("Path timetable id must match request timetableId");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Lessons saved", service.createLessons(request, principal)));
    }

    @PutMapping("/timetable-lessons/{id}/move")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<LessonView>> move(@PathVariable Long id, @Valid @RequestBody MoveLessonRequest request,
            @RequestParam(required = false) Long schoolId, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Lesson moved", service.moveLesson(id, request, schoolId, principal)));
    }

    @DeleteMapping("/timetable-lessons/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, @RequestParam(required = false) Long schoolId,
            @AuthenticationPrincipal UserPrincipal principal) {
        service.deleteLesson(id, schoolId, principal);
        return ResponseEntity.ok(ApiResponse.success("Lesson deleted", null));
    }

    @PostMapping("/timetables/{id}/publish")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<PublishView>> publish(@PathVariable Long id, @RequestParam(required = false) Long schoolId,
            @RequestBody(required = false) PublishRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Timetable published",
                service.publish(id, schoolId, request != null && request.acknowledgeIncomplete(), principal)));
    }

    @PostMapping("/timetables/{id}/unpublish")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<TimetableView>> unpublish(@PathVariable Long id, @RequestParam(required = false) Long schoolId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Timetable unpublished", service.unpublish(id, schoolId, principal)));
    }

    @GetMapping("/timetables/me")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<List<TeacherLessonView>>> myTeacherTimetable(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Teacher timetable retrieved", service.myTeacherLessons(principal)));
    }

    @GetMapping("/timetables/my-stream")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<List<StudentLessonView>>> myStudentTimetable(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Student timetable retrieved", service.myStudentLessons(principal)));
    }
}
