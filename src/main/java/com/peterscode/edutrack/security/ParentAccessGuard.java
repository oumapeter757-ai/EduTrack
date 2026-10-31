package com.peterscode.edutrack.security;

import com.peterscode.edutrack.guardian.entity.Guardian;
import com.peterscode.edutrack.guardian.entity.ParentStudentMapping;
import com.peterscode.edutrack.guardian.repository.GuardianRepository;
import com.peterscode.edutrack.guardian.repository.ParentStudentMappingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

/**
 * Centralized guard for parent-child access verification.
 * Resolves the guardian profile from the current user principal and checks
 * for an active mapping to the requested student.
 */
public final class ParentAccessGuard {

    private ParentAccessGuard() {}

    /**
     * Asserts that the given principal is a parent with an active link to the specified student.
     *
     * @param schoolId the school scope
     * @param principal the authenticated user
     * @param mappingRepo repository for parent-student mappings
     * @param guardianRepo repository for guardians
     * @param studentId the student being accessed
     * @return the guardian ID on success
     * @throws ResponseStatusException with 404 if the link does not exist (never 403)
     */
    public static Long assertLinked(
            Long schoolId,
            UserPrincipal principal,
            ParentStudentMappingRepository mappingRepo,
            GuardianRepository guardianRepo,
            Long studentId) {

        Guardian guardian = guardianRepo.findBySchoolIdAndUserId(schoolId, principal.getId())
            .orElseThrow(() -> notFound("Parent profile not found"));

        List<ParentStudentMapping> mappings =
            mappingRepo.findBySchoolIdAndParentIdAndActiveTrue(schoolId, guardian.getId());

        boolean hasActiveLink = mappings.stream()
            .anyMatch(m -> m.getStudentId().equals(studentId));

        if (!hasActiveLink) {
            throw notFound("Student not found");
        }

        return guardian.getId();
    }

    /**
     * Returns all active student IDs linked to the given parent.
     */
    public static List<Long> getLinkedStudentIds(
            Long schoolId,
            UserPrincipal principal,
            ParentStudentMappingRepository mappingRepo,
            GuardianRepository guardianRepo) {

        Optional<Guardian> guardian = guardianRepo.findBySchoolIdAndUserId(schoolId, principal.getId());
        if (guardian.isEmpty()) {
            return List.of();
        }

        List<ParentStudentMapping> mappings =
            mappingRepo.findBySchoolIdAndParentIdAndActiveTrue(schoolId, guardian.get().getId());

        return mappings.stream()
            .map(ParentStudentMapping::getStudentId)
            .toList();
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
