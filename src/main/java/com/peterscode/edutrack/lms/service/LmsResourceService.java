package com.peterscode.edutrack.lms.service;

import com.peterscode.edutrack.lms.audit.LmsAuditService;
import com.peterscode.edutrack.lms.dto.LmsRequests;
import com.peterscode.edutrack.lms.dto.LmsViews;
import com.peterscode.edutrack.lms.entity.LmsCourse;
import com.peterscode.edutrack.lms.entity.LmsResource;
import com.peterscode.edutrack.lms.entity.LmsTopic;
import com.peterscode.edutrack.lms.entity.ResourceType;
import com.peterscode.edutrack.lms.exception.LmsRuleException;
import com.peterscode.edutrack.lms.repository.LmsResourceRepository;
import com.peterscode.edutrack.lms.repository.LmsTopicRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.user.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LmsResourceService {

    private final LmsResourceRepository resourceRepository;
    private final LmsTopicRepository topicRepository;
    private final LmsAuditService auditService;
    private final LmsAccessGuard accessGuard;

    @Transactional
    public LmsViews.ResourceView createResource(Long schoolId, Long topicId, LmsRequests.CreateResourceRequest request, UserPrincipal principal) {
        LmsTopic topic = topicRepository.findBySchoolIdAndId(schoolId, topicId)
                .orElseThrow(() -> new LmsRuleException("TOPIC_NOT_FOUND", HttpStatus.NOT_FOUND, "Topic not found"));

        // Verify teacher access via course
        accessGuard.assertCourseTeacher(schoolId, topic.getCourseId(), principal);

        ResourceType type = ResourceType.valueOf(request.type());

        if (type == ResourceType.LINK && (request.url() == null || !request.url().startsWith("http"))) {
            throw new LmsRuleException("INVALID_URL", HttpStatus.BAD_REQUEST,
                    "Link resources must have a URL starting with http or https");
        }

        LmsResource resource = LmsResource.builder()
                .schoolId(schoolId)
                .topicId(topicId)
                .type(type)
                .title(request.title())
                .body(request.body())
                .url(request.url())
                .published(false)
                .createdBy(principal.getId())
                .build();

        resource = resourceRepository.save(resource);

        auditService.append(schoolId, "RESOURCE", resource.getId(), "CREATE",
                principal.getId(), null, "title:" + request.title() + ",type:" + type.name(), null);

        return toView(resource);
    }

    @Transactional
    public LmsViews.ResourceView updateResource(Long schoolId, Long resourceId, LmsRequests.UpdateResourceRequest request, UserPrincipal principal) {
        LmsResource resource = resourceRepository.findBySchoolIdAndId(schoolId, resourceId)
                .orElseThrow(() -> new LmsRuleException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "Resource not found"));

        LmsTopic topic = topicRepository.findById(resource.getTopicId())
                .orElseThrow(() -> new LmsRuleException("TOPIC_NOT_FOUND", HttpStatus.NOT_FOUND, "Topic not found"));
        accessGuard.assertCourseTeacher(schoolId, topic.getCourseId(), principal);

        if (request.title() != null) {
            resource.setTitle(request.title());
        }
        if (request.body() != null) {
            resource.setBody(request.body());
        }
        if (request.url() != null) {
            if (resource.getType() == ResourceType.LINK && !request.url().startsWith("http")) {
                throw new LmsRuleException("INVALID_URL", HttpStatus.BAD_REQUEST,
                        "Link resources must have a URL starting with http or https");
            }
            resource.setUrl(request.url());
        }

        resource = resourceRepository.save(resource);

        auditService.append(schoolId, "RESOURCE", resource.getId(), "UPDATE",
                principal.getId(), null, "title:" + resource.getTitle(), null);

        return toView(resource);
    }

    @Transactional
    public LmsViews.ResourceView publishResource(Long schoolId, Long resourceId, UserPrincipal principal) {
        LmsResource resource = resourceRepository.findBySchoolIdAndId(schoolId, resourceId)
                .orElseThrow(() -> new LmsRuleException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "Resource not found"));

        LmsTopic topic = topicRepository.findById(resource.getTopicId())
                .orElseThrow(() -> new LmsRuleException("TOPIC_NOT_FOUND", HttpStatus.NOT_FOUND, "Topic not found"));
        accessGuard.assertCourseTeacher(schoolId, topic.getCourseId(), principal);

        resource.setPublished(true);
        resource = resourceRepository.save(resource);

        auditService.append(schoolId, "RESOURCE", resource.getId(), "PUBLISH",
                principal.getId(), null, "published:true", null);

        return toView(resource);
    }

    @Transactional(readOnly = true)
    public Page<LmsViews.ResourceView> listResources(Long schoolId, Long topicId, Pageable pageable, UserPrincipal principal) {
        // Verify access
        LmsTopic topic = topicRepository.findBySchoolIdAndId(schoolId, topicId)
                .orElseThrow(() -> new LmsRuleException("TOPIC_NOT_FOUND", HttpStatus.NOT_FOUND, "Topic not found"));
        accessGuard.assertStudentInCourse(schoolId, topic.getCourseId(), principal);

        Page<LmsResource> resources = principal.getRole() == Role.STUDENT
                ? resourceRepository.findVisibleBySchoolIdAndTopicId(
                        schoolId, topicId, LocalDateTime.now(), pageable)
                : resourceRepository.findBySchoolIdAndTopicId(schoolId, topicId, pageable);
        return resources.map(this::toView);
    }

    private LmsViews.ResourceView toView(LmsResource resource) {
        return new LmsViews.ResourceView(
                resource.getId(),
                resource.getTitle(),
                resource.getType().name(),
                resource.getBody(),
                resource.getUrl(),
                resource.getPublished(),
                resource.getVisibleFrom() != null ? resource.getVisibleFrom().toString() : null
        );
    }
}
