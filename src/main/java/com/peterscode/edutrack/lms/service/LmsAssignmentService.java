package com.peterscode.edutrack.lms.service;

import com.peterscode.edutrack.lms.audit.LmsAuditService;
import com.peterscode.edutrack.lms.dto.LmsAssignmentRequests;
import com.peterscode.edutrack.lms.dto.LmsViews;
import com.peterscode.edutrack.lms.entity.*;
import com.peterscode.edutrack.lms.exception.LmsRuleException;
import com.peterscode.edutrack.lms.repository.LmsAssignmentRepository;
import com.peterscode.edutrack.lms.repository.LmsSubmissionRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.user.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class LmsAssignmentService {

    private final LmsAssignmentRepository assignmentRepository;
    private final LmsAuditService auditService;
    private final LmsAccessGuard accessGuard;
    private final Clock clock;

    @Transactional
    public LmsViews.AssignmentView createAssignment(Long schoolId, Long courseId,
                                                     LmsAssignmentRequests.CreateAssignmentRequest request,
                                                     UserPrincipal principal) {
        accessGuard.assertCourseTeacher(schoolId, courseId, principal);

        if (request.dueAt().isBefore(request.opensAt())) {
            throw new LmsRuleException("INVALID_DATES", HttpStatus.BAD_REQUEST,
                    "Due date must be after open date");
        }

        LatePolicy latePolicy = request.latePolicy() != null
                ? LatePolicy.valueOf(request.latePolicy())
                : LatePolicy.ALLOW_FLAGGED;

        LmsAssignment assignment = LmsAssignment.builder()
                .schoolId(schoolId)
                .courseId(courseId)
                .title(request.title())
                .instructions(request.instructions())
                .opensAt(request.opensAt())
                .dueAt(request.dueAt())
                .maxScore(request.maxScore())
                .latePolicy(latePolicy)
                .status(AssignmentStatus.DRAFT)
                .createdBy(principal.getId())
                .build();

        assignment = assignmentRepository.save(assignment);

        auditService.append(schoolId, "ASSIGNMENT", assignment.getId(), "CREATE",
                principal.getId(), null, "title:" + request.title(), null);

        return toView(assignment);
    }

    @Transactional
    public LmsViews.AssignmentView publishAssignment(Long schoolId, Long assignmentId, UserPrincipal principal) {
        LmsAssignment assignment = assignmentRepository.findBySchoolIdAndId(schoolId, assignmentId)
                .orElseThrow(() -> new LmsRuleException("ASSIGNMENT_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Assignment not found"));

        accessGuard.assertCourseTeacher(schoolId, assignment.getCourseId(), principal);

        assignment.setStatus(AssignmentStatus.PUBLISHED);
        assignment = assignmentRepository.save(assignment);

        auditService.append(schoolId, "ASSIGNMENT", assignment.getId(), "PUBLISH",
                principal.getId(), null, "status:PUBLISHED", null);

        return toView(assignment);
    }

    @Transactional
    public LmsViews.AssignmentView closeAssignment(Long schoolId, Long assignmentId, UserPrincipal principal) {
        LmsAssignment assignment = assignmentRepository.findBySchoolIdAndId(schoolId, assignmentId)
                .orElseThrow(() -> new LmsRuleException("ASSIGNMENT_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Assignment not found"));

        accessGuard.assertCourseTeacher(schoolId, assignment.getCourseId(), principal);

        assignment.setStatus(AssignmentStatus.CLOSED);
        assignment = assignmentRepository.save(assignment);

        auditService.append(schoolId, "ASSIGNMENT", assignment.getId(), "CLOSE",
                principal.getId(), null, "status:CLOSED", null);

        return toView(assignment);
    }

    @Transactional(readOnly = true)
    public Page<LmsViews.AssignmentView> listAssignments(Long schoolId, Long courseId, Pageable pageable,
                                                          UserPrincipal principal) {
        accessGuard.assertStudentInCourse(schoolId, courseId, principal);
        return assignmentRepository.findByCourseId(courseId, pageable).map(this::toView);
    }

    private LmsViews.AssignmentView toView(LmsAssignment a) {
        return new LmsViews.AssignmentView(
                a.getId(), a.getTitle(), a.getInstructions(),
                a.getOpensAt() != null ? a.getOpensAt().toString() : null,
                a.getDueAt() != null ? a.getDueAt().toString() : null,
                a.getMaxScore() != null ? a.getMaxScore().toString() : null,
                a.getLatePolicy().name(), a.getStatus().name()
        );
    }
}
