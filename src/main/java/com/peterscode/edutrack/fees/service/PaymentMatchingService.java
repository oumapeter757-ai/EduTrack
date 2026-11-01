package com.peterscode.edutrack.fees.service;

import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.enums.StudentStatus;
import com.peterscode.edutrack.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Matches C2B account references to students by normalized admission number.
 * Normalization: trim, uppercase, strip spaces and hyphens.
 */
@Service
@RequiredArgsConstructor
public class PaymentMatchingService {

    private final StudentRepository studentRepository;

    /**
     * Normalize an account reference for matching.
     * Matches the Student module's normalization: trim, uppercase, strip spaces and hyphens.
     */
    public static String normalizeAccountReference(String raw) {
        if (raw == null) return "";
        return raw.trim().toUpperCase().replaceAll("[\\s-]", "");
    }

    /**
     * Find a student by normalized admission number within a school.
     * Returns exactly one ACTIVE student, or empty if no match or ambiguous.
     */
    @Transactional(readOnly = true)
    public Optional<StudentMatch> findStudentByAccountReference(Long schoolId, String rawAccountRef) {
        String normalized = normalizeAccountReference(rawAccountRef);
        if (normalized.isBlank()) {
            return Optional.empty();
        }

        // Find all students with matching admission number
        List<Student> candidates = findCandidates(schoolId, normalized);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        // Filter to ACTIVE students only
        List<Student> active = candidates.stream()
                .filter(s -> s.getStatus() == StudentStatus.ACTIVE)
                .toList();

        if (active.isEmpty()) {
            // All matches are inactive — queue for manual resolution
            return Optional.empty();
        }

        if (active.size() > 1) {
            // Duplicate admission numbers — should not happen due to unique constraint, but be safe
            return Optional.empty();
        }

        Student student = active.get(0);
        return Optional.of(new StudentMatch(student.getId(), student.getAdmissionNumber()));
    }

    private List<Student> findCandidates(Long schoolId, String normalized) {
        // Try exact match first
        Optional<Student> exact = studentRepository.findBySchoolIdAndAdmissionNumber(schoolId, normalized);
        if (exact.isPresent()) {
            return List.of(exact.get());
        }
        return List.of();
    }

    /**
     * Result of a student match.
     */
    public record StudentMatch(Long studentId, String admissionNumber) {}
}
