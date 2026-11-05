package com.peterscode.edutrack.enrollment.service;

import com.peterscode.edutrack.academicyear.entity.AcademicYear;
import com.peterscode.edutrack.academicyear.repository.AcademicYearRepository;
import com.peterscode.edutrack.classlevel.entity.ClassLevel;
import com.peterscode.edutrack.classlevel.repository.ClassLevelRepository;
import com.peterscode.edutrack.enrollment.dto.PromotionRequests.CreateRun;
import com.peterscode.edutrack.enrollment.dto.PromotionRequests.OverrideBatch;
import com.peterscode.edutrack.enrollment.entity.*;
import com.peterscode.edutrack.enrollment.repository.*;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.enrollment.exception.EnrollmentRuleException;
import com.peterscode.edutrack.classlevel.entity.Stream;
import com.peterscode.edutrack.classlevel.repository.StreamRepository;
import com.peterscode.edutrack.student.repository.StudentRepository;
import org.springframework.http.HttpStatus;
import com.peterscode.edutrack.shared.exception.UnauthorizedOperationException;
import com.peterscode.edutrack.shared.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PromotionRunServiceImpl implements PromotionRunService {
    private final PromotionRunRepository runRepository;
    private final PromotionDecisionRepository decisionRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final AcademicYearRepository academicYearRepository;
    private final ClassLevelRepository classLevelRepository;
    private final StreamRepository streamRepository;
    private final PromotionExecutor executor;
    private final StudentRepository studentRepository;
    private final PromotionWarningService warningService;
    private final EnrollmentSettingsService settingsService;
    private final Clock clock;

    @Transactional
    @Override
    public PromotionRun create(CreateRun request, UserPrincipal principal) {
        requireAdmin(principal, request.getSchoolId());
        AcademicYear from = year(request.getSchoolId(), request.getFromAcademicYearId());
        AcademicYear to = year(request.getSchoolId(), request.getToAcademicYearId());
        if (!from.getEndDate().isBefore(to.getStartDate())) rule("Academic years are not sequential", "INVALID_YEAR_ORDER", HttpStatus.BAD_REQUEST);
        validateClassPath(request.getSchoolId());
        if (runRepository.existsBySchoolIdAndOpenMarker(request.getSchoolId(), 1))
            rule("An open promotion run already exists", "OPEN_RUN_EXISTS", HttpStatus.CONFLICT);
        return runRepository.save(PromotionRun.builder().schoolId(request.getSchoolId())
                .fromAcademicYearId(from.getId()).toAcademicYearId(to.getId())
                .status(PromotionRunStatus.DRAFT).createdBy(principal.getId()).openMarker(1).version(0L).build());
    }

    @Transactional
    @Override
    public PromotionRun preview(Long runId, Long schoolId, UserPrincipal principal) {
        PromotionRun run = run(runId, schoolId, principal);
        if (run.getStatus() != PromotionRunStatus.DRAFT && run.getStatus() != PromotionRunStatus.PREVIEWED)
            rule("Run cannot be previewed in its current state", "INVALID_RUN_STATE", HttpStatus.CONFLICT);
        List<StudentEnrollment> enrollments = enrollmentRepository
                .findBySchoolIdAndAcademicYearIdAndCurrentMarkerAndStatus(
                        run.getSchoolId(), run.getFromAcademicYearId(), 1, EnrollmentStatus.ENROLLED);
        Map<Long, ClassLevel> levels = classLevelRepository.findBySchoolId(run.getSchoolId(), Pageable.unpaged())
                .getContent().stream().collect(Collectors.toMap(ClassLevel::getId, Function.identity()));
        decisionRepository.deleteAll(decisionRepository.findByRunId(run.getId()));
        List<PromotionDecision> decisions = enrollments.stream().map(e -> decision(e, run, levels)).toList();
        decisionRepository.saveAll(decisions);
        run.setStatus(PromotionRunStatus.PREVIEWED);
        run.setCountsJson("{\"total\":" + decisions.size() + "}");
        return runRepository.save(run);
    }

    @Transactional(readOnly = true)
    @Override public PromotionRun get(Long runId, Long schoolId, UserPrincipal principal) {
        return run(runId, schoolId, principal);
    }

    @Transactional(readOnly = true)
    @Override public PageResponse<PromotionRun> list(Long schoolId, Pageable pageable, UserPrincipal principal) {
        requireAdmin(principal, schoolId);
        Pageable safePageable = com.peterscode.edutrack.shared.PageableSortValidator.validate(
                pageable, Set.of("id", "status", "createdAt", "startedAt", "completedAt"));
        return PageResponse.from(runRepository.findBySchoolId(schoolId, safePageable));
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<PromotionDecision> decisions(Long runId, Long schoolId, Pageable pageable, UserPrincipal principal) {
        run(runId, schoolId, principal);
        Pageable safePageable = com.peterscode.edutrack.shared.PageableSortValidator.validate(
                pageable, Set.of("id", "studentId", "proposedOutcome", "finalOutcome", "status", "appliedAt"));
        return PageResponse.from(decisionRepository.findByRunId(runId, safePageable));
    }

    @Transactional
    @Override
    public void override(Long runId, Long schoolId, OverrideBatch batch, UserPrincipal principal) {
        PromotionRun run = run(runId, schoolId, principal);
        if (run.getStatus() != PromotionRunStatus.PREVIEWED)
            rule("Overrides are closed after approval", "OVERRIDES_CLOSED", HttpStatus.CONFLICT);
        for (var item : batch.getItems()) {
            PromotionDecision decision = decisionRepository.findByRunIdAndId(runId, item.getDecisionId())
                    .orElseThrow(() -> new EnrollmentRuleException("Decision not found", "DECISION_NOT_FOUND", HttpStatus.NOT_FOUND));
            if (item.getOutcome() == PromotionOutcome.PROMOTE && item.getTargetClassLevelId() == null)
                rule("Promotions require a target class", "TARGET_CLASS_REQUIRED", HttpStatus.BAD_REQUEST);
            ClassLevel target = item.getTargetClassLevelId() == null ? null :
                    classLevelRepository.findBySchoolIdAndId(schoolId, item.getTargetClassLevelId())
                            .orElseThrow(() -> new EnrollmentRuleException("Target class not found", "TARGET_CLASS_NOT_FOUND", HttpStatus.NOT_FOUND));
            if (target != null && item.getOutcome() == PromotionOutcome.GRADUATE)
                rule("Graduation cannot have a target class", "INVALID_TARGET", HttpStatus.BAD_REQUEST);
            if (item.getOutcome() == PromotionOutcome.SKIP && item.getReason().isBlank())
                rule("Skipped decisions require a reason", "SKIP_REASON_REQUIRED", HttpStatus.BAD_REQUEST);
            if (item.getTargetStreamId() != null) {
                Stream stream = streamRepository.findBySchoolIdAndId(schoolId, item.getTargetStreamId())
                        .orElseThrow(() -> new EnrollmentRuleException("Target stream not found", "TARGET_STREAM_NOT_FOUND", HttpStatus.NOT_FOUND));
                if (target == null || !target.getId().equals(stream.getClassLevelId()) || !stream.isActive())
                    rule("Target stream does not belong to the target class", "INVALID_TARGET_STREAM", HttpStatus.BAD_REQUEST);
            }
            decision.setFinalOutcome(item.getOutcome());
            decision.setTargetClassLevelId(item.getTargetClassLevelId());
            decision.setTargetStreamId(item.getTargetStreamId());
            decision.setOverrideReason(item.getReason().trim());
        }
    }

    @Transactional
    @Override
    public PromotionRun approve(Long runId, Long schoolId, UserPrincipal principal) {
        PromotionRun run = run(runId, schoolId, principal);
        if (run.getStatus() != PromotionRunStatus.PREVIEWED)
            rule("Only previewed runs can be approved", "INVALID_RUN_STATE", HttpStatus.CONFLICT);
        List<PromotionDecision> decisions = decisionRepository.findByRunId(runId);
        if (decisions.isEmpty()) rule("Run has no decisions", "EMPTY_PREVIEW", HttpStatus.BAD_REQUEST);
        EnrollmentSettings settings = settingsService.getOrCreate(schoolId);
        for (PromotionDecision d : decisions) {
            if (d.getFinalOutcome() == null) rule("Every decision requires an outcome", "UNRESOLVED_DECISION", HttpStatus.CONFLICT);
            if ((d.getFinalOutcome() == PromotionOutcome.PROMOTE || d.getFinalOutcome() == PromotionOutcome.REPEAT)
                    && d.getTargetClassLevelId() == null)
                rule("A target class is required", "UNRESOLVED_DECISION", HttpStatus.CONFLICT);
            if ((d.getFinalOutcome() == PromotionOutcome.PROMOTE || d.getFinalOutcome() == PromotionOutcome.REPEAT)
                    && d.getTargetStreamId() == null
                    && !streamRepository.findBySchoolIdAndClassLevelId(schoolId, d.getTargetClassLevelId())
                    .stream().filter(Stream::isActive).toList().isEmpty())
                rule("A target stream is required", "UNRESOLVED_STREAM", HttpStatus.CONFLICT);
            if (d.getFinalOutcome() == PromotionOutcome.SKIP
                    && (d.getOverrideReason() == null || d.getOverrideReason().isBlank()))
                rule("Skipped decisions require a reason", "UNRESOLVED_DECISION", HttpStatus.CONFLICT);
            if (settings.isBlockOnUnpaidFees() && hasWarning(d, "unpaidFees")
                    && d.getOverrideReason() == null)
                rule("Unpaid-fee warning requires an override reason", "UNRESOLVED_WARNING", HttpStatus.CONFLICT);
            if (settings.isBlockOnOpenLibraryLoans() && hasWarning(d, "openLibraryLoans")
                    && d.getOverrideReason() == null)
                rule("Library warning requires an override reason", "UNRESOLVED_WARNING", HttpStatus.CONFLICT);
        }

        run.setStatus(PromotionRunStatus.APPROVED);
        run.setApprovedBy(principal.getId());
        run.setApprovedAt(LocalDateTime.now(clock));
        return runRepository.save(run);
    }

    private boolean hasWarning(PromotionDecision decision, String key) {
        return decision.getWarningsJson() != null
                && decision.getWarningsJson().contains("\"" + key + "\":true");
    }

    @Transactional
    @Override
    public PromotionRun cancelApproval(Long runId, Long schoolId, UserPrincipal principal) {
        PromotionRun run = run(runId, schoolId, principal);
        if (run.getStatus() != PromotionRunStatus.APPROVED)
            rule("Only approved runs can have approval cancelled", "INVALID_RUN_STATE", HttpStatus.CONFLICT);
        run.setStatus(PromotionRunStatus.PREVIEWED);
        run.setApprovedBy(null);
        run.setApprovedAt(null);
        return runRepository.save(run);
    }

    @Transactional
    @Override
    public void execute(Long runId, Long schoolId, UserPrincipal principal) {
        PromotionRun run = run(runId, schoolId, principal);
        if (run.getStatus() != PromotionRunStatus.APPROVED && run.getStatus() != PromotionRunStatus.RUNNING)
            rule("Only approved runs can execute", "INVALID_RUN_STATE", HttpStatus.CONFLICT);
        run.setStatus(PromotionRunStatus.RUNNING);
        run.setStartedAt(run.getStartedAt() == null ? LocalDateTime.now(clock) : run.getStartedAt());
        runRepository.save(run);
    }

    @Transactional(readOnly = true)
    @Override
    public PromotionRun progress(Long runId, Long schoolId, UserPrincipal principal) {
        return run(runId, schoolId, principal);
    }

    @Transactional
    @Override
    public PromotionRun revert(Long runId, Long schoolId, String reason, UserPrincipal principal) {
        PromotionRun run = run(runId, schoolId, principal);
        if (run.getStatus() != PromotionRunStatus.COMPLETED)
            rule("Only completed runs can be reverted", "INVALID_RUN_STATE", HttpStatus.CONFLICT);
        EnrollmentSettings settings = settingsService.getOrCreate(run.getSchoolId());
        if (run.getCompletedAt() == null
                || run.getCompletedAt().plusDays(settings.getUndoWindowDays()).isBefore(LocalDateTime.now(clock)))
            rule("The promotion undo window has expired", "UNDO_WINDOW_EXPIRED", HttpStatus.CONFLICT);
        if (reason == null || reason.isBlank())
            rule("A revert reason is required", "REVERT_REASON_REQUIRED", HttpStatus.BAD_REQUEST);
        run.setStatus(PromotionRunStatus.REVERTING);
        for (PromotionDecision decision : decisionRepository.findByRunId(runId)) {
            if (decision.getStatus() != PromotionDecisionStatus.APPLIED || decision.getToEnrollmentId() == null) continue;
            StudentEnrollment created = decision.getToEnrollmentId() == null ? null :
                    enrollmentRepository.findBySchoolIdAndId(run.getSchoolId(), decision.getToEnrollmentId()).orElse(null);
            StudentEnrollment previous = decision.getFromEnrollmentId() == null ? null :
                    enrollmentRepository.findBySchoolIdAndId(run.getSchoolId(), decision.getFromEnrollmentId()).orElse(null);
            if (previous == null) continue;
            if (created != null) {
                created.setCurrentMarker(null);
                created.setStatus(EnrollmentStatus.VOIDED);
                created.setEndDate(LocalDate.now(clock));
                enrollmentRepository.save(created);
            }
            previous.setCurrentMarker(1);
            previous.setEndDate(null);
            previous.setStatus(EnrollmentStatus.ENROLLED);
            enrollmentRepository.save(previous);
            studentsForRevert(run.getSchoolId(), decision.getStudentId(), previous);
            decision.setStatus(PromotionDecisionStatus.REVERTED);
            decision.setOverrideReason(reason.trim());
        }
        run.setStatus(PromotionRunStatus.REVERTED);
        run.setOpenMarker(null);
        return runRepository.save(run);
    }

    private void studentsForRevert(Long schoolId, Long studentId, StudentEnrollment previous) {
        com.peterscode.edutrack.student.entity.Student student =
                studentRepository.findBySchoolIdAndId(schoolId, studentId).orElseThrow();
        student.setAcademicYearId(previous.getAcademicYearId());
        student.setClassLevelId(previous.getClassLevelId());
        student.setStreamId(previous.getStreamId());
        student.setStatus(com.peterscode.edutrack.student.enums.StudentStatus.ACTIVE);
        student.setLeftOn(null);
        studentRepository.save(student);
    }

    private PromotionDecision decision(StudentEnrollment e, PromotionRun run, Map<Long, ClassLevel> levels) {
        ClassLevel level = e.getClassLevelId() == null ? null : levels.get(e.getClassLevelId());
        PromotionOutcome outcome = level == null ? PromotionOutcome.SKIP
                : level.isGraduating() ? PromotionOutcome.GRADUATE : PromotionOutcome.PROMOTE;
        Long target = level == null ? null : level.getNextClassLevelId();
        if (outcome == PromotionOutcome.PROMOTE && target == null) outcome = PromotionOutcome.SKIP;
        Long targetStream = null;
        if (target != null) {
            List<Stream> streams = streamRepository.findBySchoolIdAndClassLevelId(run.getSchoolId(), target)
                    .stream().filter(Stream::isActive).toList();
            if (streams.size() == 1) targetStream = streams.get(0).getId();
            else if (e.getStreamId() != null) {
                String sourceCode = streamRepository.findBySchoolIdAndId(run.getSchoolId(), e.getStreamId())
                        .map(Stream::getCode).orElse(null);
                if (sourceCode != null) targetStream = streams.stream()
                        .filter(s -> s.getCode() != null && s.getCode().trim().equalsIgnoreCase(sourceCode.trim()))
                        .map(Stream::getId).findFirst().orElse(null);
            }
        }
        var warnings = warningService.warnings(run.getSchoolId(), e.getStudentId());
        String warningJson = "{\"unpaidFees\":" + warnings.isUnpaidFees()
                + ",\"openLibraryLoans\":" + warnings.isOpenLibraryLoans()
                + ",\"incompleteMarks\":" + warnings.isIncompleteMarks() + "}";
        return PromotionDecision.builder().runId(run.getId()).studentId(e.getStudentId())
                .fromEnrollmentId(e.getId()).fromEnrollmentVersion(e.getVersion())
                .proposedOutcome(outcome).finalOutcome(outcome)
                .targetClassLevelId(target).targetStreamId(targetStream).warningsJson(warningJson)
                .status(PromotionDecisionStatus.PENDING).version(0L).build();
    }
    private AcademicYear year(Long schoolId, Long id) {
        return academicYearRepository.findBySchoolIdAndId(schoolId, id)
                .orElseThrow(() -> new IllegalArgumentException("Academic year not found"));
    }

    private void validateClassPath(Long schoolId) {
        List<ClassLevel> levels = classLevelRepository.findBySchoolIdAndActiveTrueOrderBySortOrderAsc(schoolId);
        Map<Long, ClassLevel> byId = levels.stream().collect(Collectors.toMap(ClassLevel::getId, Function.identity()));
        for (ClassLevel level : levels) {
            if (level.isGraduating() && level.getNextClassLevelId() != null)
                rule("Graduating class cannot have a next level", "INVALID_CLASS_PATH", HttpStatus.BAD_REQUEST);
            if (!level.isGraduating() && level.getNextClassLevelId() == null)
                rule("Every active non-graduating class must have a next level", "INVALID_CLASS_PATH", HttpStatus.BAD_REQUEST);
            if (level.getNextClassLevelId() != null) {
                ClassLevel next = byId.get(level.getNextClassLevelId());
                if (next == null) rule("Class path crosses school or inactive level", "INVALID_CLASS_PATH", HttpStatus.BAD_REQUEST);
            }
            Set<Long> seen = new HashSet<>();
            ClassLevel cursor = level;
            while (cursor != null && cursor.getNextClassLevelId() != null) {
                if (!seen.add(cursor.getId())) rule("Class path contains a cycle", "INVALID_CLASS_PATH", HttpStatus.BAD_REQUEST);
                cursor = byId.get(cursor.getNextClassLevelId());
            }
        }
    }
    private PromotionRun run(Long id, Long schoolId, UserPrincipal principal) {
        requireAdmin(principal, schoolId);
        return runRepository.findBySchoolIdAndId(schoolId, id)
                .orElseThrow(() -> new IllegalArgumentException("Promotion run not found"));
    }
    private void requireAdmin(UserPrincipal p, Long schoolId) {
        if (p == null || (p.getRole() != com.peterscode.edutrack.user.entity.Role.SUPER_ADMIN
                && p.getRole() != com.peterscode.edutrack.user.entity.Role.SCHOOL_ADMIN))
            throw new UnauthorizedOperationException("Access denied");
        if (p.getRole() != com.peterscode.edutrack.user.entity.Role.SUPER_ADMIN
                && !Objects.equals(p.getSchoolId(), schoolId))
            throw new UnauthorizedOperationException("Access denied");
    }
    private void rule(String message, String code, HttpStatus status) {
        throw new EnrollmentRuleException(message, code, status);
    }
}
