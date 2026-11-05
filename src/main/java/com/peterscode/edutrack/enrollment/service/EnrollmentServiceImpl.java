package com.peterscode.edutrack.enrollment.service;

import com.peterscode.edutrack.enrollment.dto.EnrollmentView;
import com.peterscode.edutrack.enrollment.entity.*;
import com.peterscode.edutrack.enrollment.repository.StudentEnrollmentRepository;
import com.peterscode.edutrack.enrollment.repository.EnrollmentAuditRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.security.ParentAccessGuard;
import com.peterscode.edutrack.guardian.repository.GuardianRepository;
import com.peterscode.edutrack.guardian.repository.ParentStudentMappingRepository;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.shared.exception.UnauthorizedOperationException;
import com.peterscode.edutrack.shared.response.PageResponse;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.enums.StudentStatus;
import com.peterscode.edutrack.student.exception.StudentNotFoundException;
import com.peterscode.edutrack.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.peterscode.edutrack.shared.PageableSortValidator;
import java.util.Set;

import java.time.LocalDate;
import java.time.Clock;

@Service
@RequiredArgsConstructor
public class EnrollmentServiceImpl implements EnrollmentService {
    private final StudentEnrollmentRepository enrollmentRepository;
    private final EnrollmentAuditRepository auditRepository;
    private final Clock clock;
    private final StudentRepository studentRepository;
    private final GuardianRepository guardianRepository;
    private final ParentStudentMappingRepository mappingRepository;

    @Override
    public void recordAdmission(Student student) {
        if (enrollmentRepository.findBySchoolIdAndStudentIdAndCurrentMarker(student.getSchoolId(), student.getId(), 1).isPresent()) return;
        StudentEnrollment enrollment = enrollmentRepository.save(StudentEnrollment.builder()
                .schoolId(student.getSchoolId()).studentId(student.getId())
                .academicYearId(student.getAcademicYearId()).classLevelId(student.getClassLevelId())
                .streamId(student.getStreamId()).status(toEnrollmentStatus(student.getStatus()))
                .startDate(student.getAdmissionDate() == null ? LocalDate.now(clock) : student.getAdmissionDate())
                .source(EnrollmentSource.ADMISSION).currentMarker(1).version(0L).build());
        audit(student, "ADMISSION", null, enrollment.getStatus().name(), null);
    }

    @Override
    public void synchronizePlacement(Student student, Long oldYearId, Long oldClassId, Long oldStreamId) {
        if (same(oldYearId, student.getAcademicYearId())) {
            if (!same(oldClassId, student.getClassLevelId()) || !same(oldStreamId, student.getStreamId())) {
                audit(student, "PLACEMENT_AUDIT", oldClassId + "/" + oldStreamId,
                        student.getClassLevelId() + "/" + student.getStreamId(), "MID_YEAR_PLACEMENT_CHANGE");
            }
            return;
        }
        if (student.getStatus() == StudentStatus.ACTIVE) {
            closeAndOpen(student, EnrollmentStatus.ENROLLED, "ACADEMIC_YEAR_CHANGED");
        }
    }

    @Override
    public void synchronizeStatus(Student student, StudentStatus oldStatus, UserPrincipal principal) {
        if (oldStatus == student.getStatus()) return;
        boolean oldActive = oldStatus == StudentStatus.ACTIVE;
        boolean newActive = student.getStatus() == StudentStatus.ACTIVE;
        if (!newActive) {
            enrollmentRepository.closeCurrent(student.getSchoolId(), student.getId(), LocalDate.now(clock),
                    toEnrollmentStatus(student.getStatus()), student.getStatus().name());
            audit(student, "STATUS_CHANGE", oldStatus.name(), student.getStatus().name(), student.getStatus().name());
        } else if (!oldActive) {
            closeAndOpen(student, EnrollmentStatus.ENROLLED, "REACTIVATED");
            audit(student, "STATUS_CHANGE", oldStatus.name(), student.getStatus().name(), "REACTIVATED");
        }
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<EnrollmentView> history(Long studentId, Long requestedSchoolId, Pageable pageable, UserPrincipal principal) {
        if (principal == null || principal.getRole() == null) throw new UnauthorizedOperationException("Access denied");
        Long schoolId = principal.getRole() == Role.SUPER_ADMIN ? requestedSchoolId : principal.getSchoolId();
        if (schoolId == null) throw new UnauthorizedOperationException("School context is required");
        Student student = studentRepository.findBySchoolIdAndId(schoolId, studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
        if (principal.getRole() == Role.STUDENT
                && (student.getUserId() == null || !student.getUserId().equals(principal.getId())))
            throw new StudentNotFoundException(studentId);
        if (principal.getRole() == Role.PARENT) {
            ParentAccessGuard.assertLinked(schoolId, principal, mappingRepository, guardianRepository, studentId);
        }
        if (principal.getRole() == Role.TEACHER) {
            throw new UnauthorizedOperationException("Teacher roster access is not available for this endpoint");
        }
        if (principal.getRole() != Role.SUPER_ADMIN && principal.getRole() != Role.SCHOOL_ADMIN
                && principal.getRole() != Role.STUDENT && principal.getRole() != Role.PARENT) {
            throw new UnauthorizedOperationException("Access denied");
        }
        Pageable safePageable = PageableSortValidator.validate(pageable,
                Set.of("id", "academicYearId", "classLevelId", "streamId", "status", "startDate", "endDate"));
        Page<EnrollmentView> page = enrollmentRepository.findBySchoolIdAndStudentId(schoolId, studentId, safePageable)
                .map(this::toView);
        return PageResponse.from(page);
    }

    private void closeAndOpen(Student student, EnrollmentStatus nextStatus, String reason) {
        enrollmentRepository.closeCurrent(student.getSchoolId(), student.getId(), LocalDate.now(clock),
                nextStatus, reason);
        enrollmentRepository.save(StudentEnrollment.builder()
                .schoolId(student.getSchoolId()).studentId(student.getId())
                .academicYearId(student.getAcademicYearId()).classLevelId(student.getClassLevelId())
                .streamId(student.getStreamId()).status(nextStatus).startDate(LocalDate.now(clock))
                .source(EnrollmentSource.MANUAL).currentMarker(1).version(0L).build());
    }

    private EnrollmentStatus toEnrollmentStatus(StudentStatus status) {
        return switch (status) {
            case ACTIVE -> EnrollmentStatus.ENROLLED;
            case GRADUATED -> EnrollmentStatus.GRADUATED;
            case TRANSFERRED -> EnrollmentStatus.TRANSFERRED;
            case WITHDRAWN, EXPELLED -> EnrollmentStatus.WITHDRAWN;
            case SUSPENDED -> EnrollmentStatus.SUSPENDED;
            case INACTIVE -> EnrollmentStatus.WITHDRAWN;
        };
    }
    private boolean same(Long a, Long b) { return a == null ? b == null : a.equals(b); }
    private EnrollmentView toView(StudentEnrollment e) {
        return EnrollmentView.builder().id(e.getId()).schoolId(e.getSchoolId()).studentId(e.getStudentId())
                .academicYearId(e.getAcademicYearId()).classLevelId(e.getClassLevelId()).streamId(e.getStreamId())
                .status(e.getStatus()).startDate(e.getStartDate()).endDate(e.getEndDate())
                .outcomeReason(e.getOutcomeReason()).source(e.getSource()).build();
    }
    private void audit(Student student, String action, String oldValue, String newValue, String reason) {
        auditRepository.save(EnrollmentAudit.builder().schoolId(student.getSchoolId()).entityType("STUDENT")
                .entityId(student.getId()).action(action).actorId(null).oldValue(oldValue)
                .newValue(newValue).reason(reason).occurredAt(java.time.LocalDateTime.now()).build());
    }
}
