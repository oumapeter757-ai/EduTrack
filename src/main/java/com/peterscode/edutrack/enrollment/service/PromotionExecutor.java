package com.peterscode.edutrack.enrollment.service;

import com.peterscode.edutrack.enrollment.entity.*;
import com.peterscode.edutrack.enrollment.repository.*;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.enums.StudentStatus;
import com.peterscode.edutrack.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.peterscode.edutrack.enrollment.event.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Clock;

@Service
@RequiredArgsConstructor
public class PromotionExecutor {
    private final PromotionDecisionRepository decisions;
    private final PromotionRunRepository runs;
    private final StudentRepository students;
    private final StudentEnrollmentRepository enrollments;
    private final EnrollmentAuditRepository audits;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public void apply(Long runId, Long decisionId) {
        PromotionDecision d = decisions.findLockedByRunIdAndId(runId, decisionId).orElseThrow();
        if (d.getStatus() != PromotionDecisionStatus.PENDING) return;
        PromotionRun run = runs.findById(runId).orElseThrow();
        Student student = students.findLockedBySchoolIdAndId(run.getSchoolId(), d.getStudentId()).orElseThrow();
        StudentEnrollment current = enrollments.findBySchoolIdAndStudentIdAndCurrentMarker(
                run.getSchoolId(), student.getId(), 1).orElse(null);
        if (current == null || !current.getId().equals(d.getFromEnrollmentId())
                || !current.getVersion().equals(d.getFromEnrollmentVersion())
                || student.getStatus() != StudentStatus.ACTIVE) {
            d.setStatus(PromotionDecisionStatus.STALE);
            return;
        }
        PromotionOutcome outcome = d.getFinalOutcome() == null ? d.getProposedOutcome() : d.getFinalOutcome();
        LocalDate today = LocalDate.now(clock);
        EnrollmentStatus closed = switch (outcome) {
            case GRADUATE -> EnrollmentStatus.GRADUATED;
            case TRANSFER_OUT -> EnrollmentStatus.TRANSFERRED;
            case WITHDRAW -> EnrollmentStatus.WITHDRAWN;
            case REPEAT -> EnrollmentStatus.REPEATED;
            case PROMOTE -> EnrollmentStatus.PROMOTED;
            case SKIP -> null;
        };
        if (outcome == PromotionOutcome.SKIP) {
            d.setStatus(PromotionDecisionStatus.SKIPPED);
            return;
        }
        current.setCurrentMarker(null);
        current.setEndDate(today);
        current.setStatus(closed);
        current.setOutcomeReason(d.getOverrideReason());
        if (outcome == PromotionOutcome.GRADUATE || outcome == PromotionOutcome.TRANSFER_OUT
                || outcome == PromotionOutcome.WITHDRAW) {
            student.setStatus(switch (outcome) {
                case GRADUATE -> StudentStatus.GRADUATED;
                case TRANSFER_OUT -> StudentStatus.TRANSFERRED;
                default -> StudentStatus.WITHDRAWN;
            });
            student.setLeftOn(today);
        } else {
            StudentEnrollment next = enrollments.save(StudentEnrollment.builder()
                    .schoolId(run.getSchoolId()).studentId(student.getId())
                    .academicYearId(run.getToAcademicYearId()).classLevelId(d.getTargetClassLevelId())
                    .streamId(d.getTargetStreamId()).status(EnrollmentStatus.ENROLLED)
                    .startDate(today).source(EnrollmentSource.PROMOTION)
                    .promotionRunId(runId).currentMarker(1).version(0L).build());
            student.setClassLevelId(next.getClassLevelId());
            student.setStreamId(next.getStreamId());
            student.setAcademicYearId(next.getAcademicYearId());
        }
        students.save(student);
        audits.save(EnrollmentAudit.builder().schoolId(run.getSchoolId()).entityType("PROMOTION_DECISION")
                .entityId(d.getId()).action(outcome.name()).actorId(run.getCreatedBy())
                .oldValue(current.getId().toString()).newValue(student.getStatus().name())
                .occurredAt(LocalDateTime.now(clock)).build());
        d.setToEnrollmentId(outcome == PromotionOutcome.PROMOTE || outcome == PromotionOutcome.REPEAT
                ? enrollments.findBySchoolIdAndStudentIdAndCurrentMarker(run.getSchoolId(), student.getId(), 1).map(StudentEnrollment::getId).orElse(null) : null);
        d.setAppliedAt(LocalDateTime.now(clock));
        d.setStatus(PromotionDecisionStatus.APPLIED);
        if (outcome == PromotionOutcome.PROMOTE) {
            events.publishEvent(new StudentPromotedEvent(run.getSchoolId(), student.getId(), current.getId(),
                    d.getToEnrollmentId(), runId));
        } else if (outcome == PromotionOutcome.REPEAT) {
            events.publishEvent(new StudentRepeatedEvent(run.getSchoolId(), student.getId(), current.getId(),
                    d.getToEnrollmentId(), runId));
        } else if (outcome == PromotionOutcome.GRADUATE) {
            events.publishEvent(new StudentGraduatedEvent(run.getSchoolId(), student.getId(), current.getId(), runId));
        } else {
            events.publishEvent(new StudentExitedEvent(run.getSchoolId(), student.getId(), current.getId(),
                    outcome.name(), runId));
        }
    }
}
