package com.peterscode.edutrack.lms.controller;

import com.peterscode.edutrack.lms.audit.LmsAuditService;
import com.peterscode.edutrack.lms.dto.LmsViews;
import com.peterscode.edutrack.lms.entity.LmsResource;
import com.peterscode.edutrack.lms.exception.LmsRuleException;
import com.peterscode.edutrack.lms.repository.LmsResourceRepository;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.security.RequiresModule;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/lms")
@RequiredArgsConstructor
@RequiresModule(ServiceModule.LMS)
public class LmsModerationController {

    private final LmsModerationService moderationService;

    @PostMapping("/moderate/hide")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> hideContent(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        String entityType = (String) request.get("entityType");
        Long entityId = ((Number) request.get("entityId")).longValue();
        String reason = (String) request.get("reason");

        if (entityType == null || entityId == null || reason == null || reason.isBlank()) {
            throw new LmsRuleException("INVALID_REQUEST", HttpStatus.BAD_REQUEST,
                    "entityType, entityId, and reason are required");
        }

        moderationService.hideContent(principal.getSchoolId(), entityType, entityId, reason, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Content hidden", null));
    }

    @PostMapping("/moderate/remove")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> removeContent(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        String entityType = (String) request.get("entityType");
        Long entityId = ((Number) request.get("entityId")).longValue();
        String reason = (String) request.get("reason");

        if (entityType == null || entityId == null || reason == null || reason.isBlank()) {
            throw new LmsRuleException("INVALID_REQUEST", HttpStatus.BAD_REQUEST,
                    "entityType, entityId, and reason are required");
        }

        moderationService.removeContent(principal.getSchoolId(), entityType, entityId, reason, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Content removed", null));
    }
}

@Service
@RequiredArgsConstructor
class LmsModerationService {

    private final LmsResourceRepository resourceRepository;
    private final LmsAuditService auditService;

    @Transactional
    public void hideContent(Long schoolId, String entityType, Long entityId, String reason, Long actorId) {
        if ("RESOURCE".equals(entityType)) {
            LmsResource resource = resourceRepository.findBySchoolIdAndId(schoolId, entityId)
                    .orElseThrow(() -> new LmsRuleException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND,
                            "Resource not found"));
            resource.setPublished(false);
            resourceRepository.save(resource);
        }

        auditService.append(schoolId, entityType, entityId, "HIDE",
                actorId, null, "reason:" + reason, reason);
    }

    @Transactional
    public void removeContent(Long schoolId, String entityType, Long entityId, String reason, Long actorId) {
        // Soft delete - mark as removed via audit
        // Actual removal depends on entity type
        auditService.append(schoolId, entityType, entityId, "REMOVE",
                actorId, null, "reason:" + reason, reason);
    }
}
