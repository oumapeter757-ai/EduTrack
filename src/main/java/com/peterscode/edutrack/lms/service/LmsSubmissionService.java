package com.peterscode.edutrack.lms.service;

import com.peterscode.edutrack.lms.audit.LmsAuditService;
import com.peterscode.edutrack.lms.dto.LmsAssignmentRequests;
import com.peterscode.edutrack.lms.dto.LmsViews;
import com.peterscode.edutrack.lms.entity.*;
import com.peterscode.edutrack.lms.exception.LmsRuleException;
import com.peterscode.edutrack.lms.repository.LmsAssignmentRepository;
import com.peterscode.edutrack.lms.repository.LmsSubmissionRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.student.repository.StudentRepository;
import com.peterscode.edutrack.user.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LmsSubmissionService {

    private final LmsSubmissionRepository submissionRepository;
    private final LmsAssignmentRepository assignmentRepository;
    private final LmsAuditService auditService;
    private final LmsAccessGuard accessGuard;
    private final Clock clock;
    private final StudentRepository studentRepository;

    @Transactional
    public LmsViews.SubmissionView submit(Long schoolId, Long assignmentId,
                                           LmsAssignmentRequests.SubmitAssignmentRequest request,
                                           UserPrincipal principal) {
        if (principal.getRole() != Role.STUDENT) {
            throw new LmsRuleException("ACCESS_DENIED", HttpStatus.FORBIDDEN, "Only students can submit");
        }

        LmsAssignment assignment = assignmentRepository.findBySchoolIdAndId(schoolId, assignmentId)
                .orElseThrow(() -> new LmsRuleException("ASSIGNMENT_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Assignment not found"));

        // Verify student is in the course
        accessGuard.assertStudentInCourse(schoolId, assignment.getCourseId(), principal);

        // Check assignment status
        if (assignment.getStatus() == AssignmentStatus.CLOSED) {
            throw new LmsRuleException("ASSIGNMENT_CLOSED", HttpStatus.BAD_REQUEST,
                    "Assignment is closed");
        }
        if (assignment.getStatus() != AssignmentStatus.PUBLISHED) {
            throw new LmsRuleException("ASSIGNMENT_NOT_PUBLISHED", HttpStatus.BAD_REQUEST,
                    "Assignment is not published");
        }

        LocalDateTime now = LocalDateTime.now(clock);

        // Check opens_at
        if (now.isBefore(assignment.getOpensAt())) {
            throw new LmsRuleException("NOT_YET_OPEN", HttpStatus.BAD_REQUEST,
                    "Assignment has not opened yet");
        }

        // Check for existing submission (resubmission)
        Optional<LmsSubmission> existing = submissionRepository
                .findBySchoolIdAndAssignmentIdAndStudentId(schoolId, assignmentId, principal.getId());

        if (existing.isPresent()) {
            LmsSubmission submission = existing.get();
            if (submission.getStatus() == SubmissionStatus.MARKED) {
                throw new LmsRuleException("ALREADY_MARKED", HttpStatus.BAD_REQUEST,
                        "Cannot resubmit a marked assignment");
            }

            // Check if late
            boolean isLate = now.isAfter(assignment.getDueAt());
            if (isLate && assignment.getLatePolicy() == LatePolicy.BLOCK) {
                throw new LmsRuleException("LATE_SUBMISSION_BLOCKED", HttpStatus.BAD_REQUEST,
                        "Late submissions are not allowed for this assignment");
            }

            submission.setAnswerText(request.answerText());
            submission.setSubmittedAt(now);
            submission.setLate(isLate);
            submission.setAttemptCount(submission.getAttemptCount() + 1);
            submission.setStatus(SubmissionStatus.SUBMITTED);

            submission = submissionRepository.save(submission);

            auditService.append(schoolId, "SUBMISSION", submission.getId(), "RESUBMIT",
                    principal.getId(), null,
                    "attempt:" + submission.getAttemptCount() + ",late:" + isLate, null);

            return toView(submission);
        }

        // Check if late for new submission
        boolean isLate = now.isAfter(assignment.getDueAt());
        if (isLate && assignment.getLatePolicy() == LatePolicy.BLOCK) {
            throw new LmsRuleException("LATE_SUBMISSION_BLOCKED", HttpStatus.BAD_REQUEST,
                    "Late submissions are not allowed for this assignment");
        }

        LmsSubmission submission = LmsSubmission.builder()
                .schoolId(schoolId)
                .assignmentId(assignmentId)
                .studentId(principal.getId())
                .answerText(request.answerText())
                .submittedAt(now)
                .late(isLate)
                .attemptCount(1)
                .status(SubmissionStatus.SUBMITTED)
                .released(false)
                .build();

        submission = submissionRepository.save(submission);

        auditService.append(schoolId, "SUBMISSION", submission.getId(), "SUBMIT",
                principal.getId(), null, "late:" + isLate, null);

        return toView(submission);
    }

    @Transactional(readOnly = true)
    public LmsViews.SubmissionView getSubmission(Long schoolId, Long submissionId, UserPrincipal principal) {
        LmsSubmission submission = submissionRepository.findBySchoolIdAndId(schoolId, submissionId)
                .orElseThrow(() -> new LmsRuleException("SUBMISSION_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Submission not found"));

        // Student can only see their own submission
        if (principal.getRole() == Role.STUDENT) {
            if (!submission.getStudentId().equals(principal.getId())) {
                throw new LmsRuleException("SUBMISSION_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Submission not found");
            }
        }

        // Teacher/admin can see any submission in their school
        if (principal.getRole() == Role.TEACHER || principal.getRole() == Role.SCHOOL_ADMIN
                || principal.getRole() == Role.SUPER_ADMIN) {
            // Verify teacher is for this course
            LmsAssignment assignment = assignmentRepository.findById(submission.getAssignmentId())
                    .orElseThrow(() -> new LmsRuleException("ASSIGNMENT_NOT_FOUND", HttpStatus.NOT_FOUND,
                            "Assignment not found"));
            accessGuard.assertCourseTeacher(schoolId, assignment.getCourseId(), principal);
        }

        return toView(submission);
    }

    @Transactional(readOnly = true)
    public Page<LmsViews.SubmissionView> listMySubmissions(Long schoolId, Pageable pageable,
                                                            UserPrincipal principal) {
        if (principal.getRole() != Role.STUDENT) {
            throw new LmsRuleException("ACCESS_DENIED", HttpStatus.FORBIDDEN, "Only students can view their submissions");
        }
        Long studentId = studentRepository.findBySchoolIdAndUserId(schoolId, principal.getId())
                .orElseThrow(() -> new LmsRuleException("STUDENT_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Student not found")).getId();
        return submissionRepository.findBySchoolIdAndStudentId(schoolId, studentId, pageable).map(this::toView);
    }

    @Transactional(readOnly = true)
    public Page<LmsViews.SubmissionView> listCourseSubmissions(Long schoolId, Long assignmentId,
                                                                 Pageable pageable,
                                                                 UserPrincipal principal) {
        LmsAssignment assignment = assignmentRepository.findBySchoolIdAndId(schoolId, assignmentId)
                .orElseThrow(() -> new LmsRuleException("ASSIGNMENT_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Assignment not found"));
        accessGuard.assertCourseTeacher(schoolId, assignment.getCourseId(), principal);
        return submissionRepository.findByAssignmentId(assignmentId, pageable).map(this::toView);
    }

    private LmsViews.SubmissionView toView(LmsSubmission s) {
        return new LmsViews.SubmissionView(
                s.getId(), s.getAssignmentId(), s.getAnswerText(),
                s.getSubmittedAt() != null ? s.getSubmittedAt().toString() : null,
                s.getLate(), s.getAttemptCount(), s.getStatus().name(),
                s.getScore() != null ? s.getScore().toString() : null,
                s.getFeedback(), s.getReleased()
        );
    }
}
