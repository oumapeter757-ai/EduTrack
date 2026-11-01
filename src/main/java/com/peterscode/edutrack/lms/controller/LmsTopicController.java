package com.peterscode.edutrack.lms.controller;

import com.peterscode.edutrack.lms.dto.LmsRequests;
import com.peterscode.edutrack.lms.dto.LmsViews;
import com.peterscode.edutrack.lms.entity.LmsTopic;
import com.peterscode.edutrack.lms.exception.LmsRuleException;
import com.peterscode.edutrack.lms.repository.LmsTopicRepository;
import com.peterscode.edutrack.lms.service.LmsAccessGuard;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.security.RequiresModule;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.ApiResponse;
import jakarta.validation.Valid;
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
public class LmsTopicController {

    private final LmsTopicService topicService;

    @PostMapping("/courses/{courseId}/topics")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LmsViews.TopicView>> createTopic(
            @PathVariable Long courseId,
            @Valid @RequestBody LmsRequests.CreateTopicRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        LmsViews.TopicView view = topicService.createTopic(principal.getSchoolId(), courseId, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Topic created", view));
    }

    @GetMapping("/courses/{courseId}/topics")
    @PreAuthorize("hasAnyRole('TEACHER', 'STUDENT', 'SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<LmsViews.TopicView>>> listTopics(
            @PathVariable Long courseId,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<LmsViews.TopicView> views = topicService.listTopics(principal.getSchoolId(), courseId, principal);
        return ResponseEntity.ok(ApiResponse.success("Topics", views));
    }
}

@Service
@RequiredArgsConstructor
class LmsTopicService {

    private final LmsTopicRepository topicRepository;
    private final LmsAccessGuard accessGuard;

    @Transactional
    public LmsViews.TopicView createTopic(Long schoolId, Long courseId, LmsRequests.CreateTopicRequest request, UserPrincipal principal) {
        accessGuard.assertCourseTeacher(schoolId, courseId, principal);

        Integer maxPos = topicRepository.findMaxPositionByCourseId(courseId).orElse(0);

        LmsTopic topic = LmsTopic.builder()
                .schoolId(schoolId)
                .courseId(courseId)
                .title(request.title())
                .position(maxPos + 1)
                .build();

        topic = topicRepository.save(topic);
        return toView(topic);
    }

    @Transactional(readOnly = true)
    public List<LmsViews.TopicView> listTopics(Long schoolId, Long courseId, UserPrincipal principal) {
        accessGuard.assertStudentInCourse(schoolId, courseId, principal);
        return topicRepository.findByCourseIdOrderByPosition(courseId).stream()
                .map(this::toView)
                .toList();
    }

    private LmsViews.TopicView toView(LmsTopic topic) {
        return new LmsViews.TopicView(topic.getId(), topic.getTitle(), topic.getPosition());
    }
}
