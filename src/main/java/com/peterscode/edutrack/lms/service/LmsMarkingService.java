package com.peterscode.edutrack.lms.service;

import com.peterscode.edutrack.lms.audit.LmsAuditService;
import com.peterscode.edutrack.lms.dto.LmsAssignmentRequests;
import com.peterscode.edutrack.lms.dto.LmsViews;
import com.peterscode.edutrack.lms.entity.LmsAssignment;
import com.peterscode.edutrack.lms.entity.LmsSubmission;
import com.peterscode.edutrack.lms.entity.SubmissionStatus;
import com.peterscode.edutrack.lms.exception.LmsRuleException;
import com.peterscode.edutrack.lms.repository.LmsAssignmentRepository;
import com.peterscode.edutrack.lms.repository.LmsSubmissionRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LmsMarkingService {

    private final LmsSubmissionRepository submissionRepository;
    private final LmsAssignmentRepository assignmentRepository;
    private final LmsAuditService auditService;
    private final LmsAccessGuard accessGuard;
    private final Clock clock;

    @Transactional
    public LmsViews.SubmissionView markSubmission(Long schoolId, Long submissionId,
                                                   LmsAssignmentRequests.MarkSubmissionRequest request,
                                                   UserPrincipal principal) {
        LmsSubmission submission = submissionRepository.findBySchoolIdAndId(schoolId, submissionId)
                .orElseThrow(() -> new LmsRuleException("SUBMISSION_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Submission not found"));

        LmsAssignment assignment = assignmentRepository.findById(submission.getAssignmentId())
                .orElseThrow(() -> new LmsRuleException("ASSIGNMENT_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Assignment not found"));
        accessGuard.assertCourseTeacher(schoolId, assignment.getCourseId(), principal);

        // Validate score
        if (request.score().compareTo(BigDecimal.ZERO) < 0 ||
                request.score().compareTo(assignment.getMaxScore()) > 0) {
            throw new LmsRuleException("INVALID_SCORE", HttpStatus.BAD_REQUEST,
                    "Score must be between 0 and " + assignment.getMaxScore());
        }

        submission.setScore(request.score());
        submission.setFeedback(request.feedback());
        submission.setStatus(SubmissionStatus.MARKED);
        submission.setMarkedBy(principal.getId());
        submission.setMarkedAt(LocalDateTime.now(clock));

        submission = submissionRepository.save(submission);

        auditService.append(schoolId, "SUBMISSION", submission.getId(), "MARK",
                principal.getId(), null,
                "score:" + request.score() + ",feedback:" + (request.feedback() != null ? request.feedback().length() : 0) + "chars", null);

        return toView(submission);
    }

    @Transactional
    public LmsViews.SubmissionView releaseSubmission(Long schoolId, Long submissionId, UserPrincipal principal) {
        LmsSubmission submission = submissionRepository.findBySchoolIdAndId(schoolId, submissionId)
                .orElseThrow(() -> new LmsRuleException("SUBMISSION_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Submission not found"));

        LmsAssignment assignment = assignmentRepository.findById(submission.getAssignmentId())
                .orElseThrow(() -> new LmsRuleException("ASSIGNMENT_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Assignment not found"));
        accessGuard.assertCourseTeacher(schoolId, assignment.getCourseId(), principal);

        submission.setReleased(true);
        submission = submissionRepository.save(submission);

        auditService.append(schoolId, "SUBMISSION", submission.getId(), "RELEASE",
                principal.getId(), null, "released:true", null);

        return toView(submission);
    }

    @Transactional
    public void releaseAll(Long schoolId, Long assignmentId, UserPrincipal principal) {
        LmsAssignment assignment = assignmentRepository.findBySchoolIdAndId(schoolId, assignmentId)
                .orElseThrow(() -> new LmsRuleException("ASSIGNMENT_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Assignment not found"));
        accessGuard.assertCourseTeacher(schoolId, assignment.getCourseId(), principal);

        var submissions = submissionRepository.findByAssignmentId(assignmentId,
                org.springframework.data.domain.PageRequest.of(0, 1000)).getContent();
        for (LmsSubmission s : submissions) {
            if (s.getStatus() == SubmissionStatus.MARKED && !s.getReleased()) {
                s.setReleased(true);
                submissionRepository.save(s);
                auditService.append(schoolId, "SUBMISSION", s.getId(), "RELEASE",
                        principal.getId(), null, "released:true,bulk:true", null);
            }
        }
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
