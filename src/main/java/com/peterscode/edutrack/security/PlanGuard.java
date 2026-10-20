package com.peterscode.edutrack.security;

import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.school.service.PlanCatalog;
import com.peterscode.edutrack.student.enums.StudentStatus;
import com.peterscode.edutrack.student.repository.StudentRepository;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.repository.UserRepository;
import com.peterscode.edutrack.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.transaction.annotation.Propagation.MANDATORY;

@Component
@RequiredArgsConstructor
public class PlanGuard {
    private final SchoolRepository schoolRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final PlanCatalog planCatalog;

    @Transactional(propagation = MANDATORY)
    public void assertStudentCapacity(Long schoolId, int additionalActiveStudents) {
        if (additionalActiveStudents <= 0) return;
        School school = schoolRepository.findByIdForUpdate(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found: " + schoolId));
        Integer limit = effectiveLimit(school, false);
        if (limit == null) return;
        long active = studentRepository.countBySchoolIdAndStatus(schoolId, StudentStatus.ACTIVE);
        if (active + additionalActiveStudents > limit) {
            throw new PlanLimitReachedException("Student plan limit reached", "LIMIT_REACHED");
        }
    }

    @Transactional(propagation = MANDATORY)
    public void assertTeacherCapacity(Long schoolId, int additionalActiveTeachers) {
        if (additionalActiveTeachers <= 0) return;
        School school = schoolRepository.findByIdForUpdate(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found: " + schoolId));
        Integer limit = effectiveLimit(school, true);
        if (limit == null) return;
        long active = userRepository.countBySchoolIdAndRoleAndActiveTrue(schoolId, Role.TEACHER);
        if (active + additionalActiveTeachers > limit) {
            throw new PlanLimitReachedException("Teacher plan limit reached", "LIMIT_REACHED");
        }
    }

    public Integer effectiveStudentLimit(School school) {
        return effectiveLimit(school, false);
    }

    public Integer effectiveTeacherLimit(School school) {
        return effectiveLimit(school, true);
    }

    /**
     * Returns the stored positive override, or the plan default when no override is stored.
     * ENTERPRISE is unlimited and a null tier fails closed through PlanCatalog.
     */
    private Integer effectiveLimit(School school, boolean teachers) {
        if (school == null) throw new ResourceNotFoundException("School not found");
        if (teachers) {
            if (planCatalog.unlimitedTeachers(school.getSubscriptionTier())) return null;
            return school.getMaxTeachers() != null
                    ? school.getMaxTeachers() : planCatalog.maxTeachers(school.getSubscriptionTier());
        }
        if (planCatalog.unlimitedStudents(school.getSubscriptionTier())) return null;
        return school.getMaxStudents() != null
                ? school.getMaxStudents() : planCatalog.maxStudents(school.getSubscriptionTier());
    }
}
