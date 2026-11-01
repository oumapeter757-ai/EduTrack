package com.peterscode.edutrack.lms.controller;

import com.peterscode.edutrack.lms.audit.LmsAuditService;
import com.peterscode.edutrack.lms.dto.LmsViews;
import com.peterscode.edutrack.lms.entity.LmsAnnouncement;
import com.peterscode.edutrack.lms.exception.LmsRuleException;
import com.peterscode.edutrack.lms.repository.LmsAnnouncementRepository;
import com.peterscode.edutrack.lms.service.LmsAccessGuard;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.security.RequiresModule;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/lms")
@RequiredArgsConstructor
@RequiresModule(ServiceModule.LMS)
public class LmsAnnouncementController {

    private final LmsAnnouncementService announcementService;

    @PostMapping("/courses/{courseId}/announcements")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LmsViews.AnnouncementView>> createAnnouncement(
            @PathVariable Long courseId,
            @RequestBody LmsViews.CreateAnnouncementRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        LmsViews.AnnouncementView view = announcementService.createAnnouncement(principal.getSchoolId(), courseId, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Announcement created", view));
    }

    @GetMapping("/courses/{courseId}/announcements")
    @PreAuthorize("hasAnyRole('TEACHER', 'STUDENT', 'SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<LmsViews.AnnouncementView>>> listAnnouncements(
            @PathVariable Long courseId,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<LmsViews.AnnouncementView> views = announcementService.listAnnouncements(principal.getSchoolId(), courseId, principal);
        return ResponseEntity.ok(ApiResponse.success("Announcements", views));
    }
}

@Service
@RequiredArgsConstructor
class LmsAnnouncementService {

    private final LmsAnnouncementRepository announcementRepository;
    private final LmsAuditService auditService;
    private final LmsAccessGuard accessGuard;

    @Transactional
    public LmsViews.AnnouncementView createAnnouncement(Long schoolId, Long courseId,
                                                         LmsViews.CreateAnnouncementRequest request,
                                                         UserPrincipal principal) {
        accessGuard.assertCourseTeacher(schoolId, courseId, principal);

        LmsAnnouncement announcement = LmsAnnouncement.builder()
                .schoolId(schoolId)
                .courseId(courseId)
                .title(request.title())
                .body(request.body())
                .postedBy(principal.getId())
                .build();

        announcement = announcementRepository.save(announcement);

        auditService.append(schoolId, "ANNOUNCEMENT", announcement.getId(), "CREATE",
                principal.getId(), null, "title:" + request.title(), null);

        return toView(announcement);
    }

    @Transactional(readOnly = true)
    public List<LmsViews.AnnouncementView> listAnnouncements(Long schoolId, Long courseId,
                                                              UserPrincipal principal) {
        accessGuard.assertStudentInCourse(schoolId, courseId, principal);
        return announcementRepository.findByCourseId(courseId).stream()
                .map(this::toView)
                .toList();
    }

    private LmsViews.AnnouncementView toView(LmsAnnouncement a) {
        return new LmsViews.AnnouncementView(a.getId(), a.getTitle(), a.getBody(),
                a.getPostedBy(), a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
    }
}
