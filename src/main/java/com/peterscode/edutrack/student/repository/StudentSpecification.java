package com.peterscode.edutrack.student.repository;

import com.peterscode.edutrack.student.dto.StudentSearchCriteria;
import com.peterscode.edutrack.student.entity.Student;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import com.peterscode.edutrack.student.exception.StudentRequestException;
import com.peterscode.edutrack.shared.PhoneNumberNormalizer;

public class StudentSpecification {

    private StudentSpecification() {
    }

    public static Specification<Student> withCriteria(StudentSearchCriteria criteria, Long enforcedSchoolId) {
        return (root, query, criteriaBuilder) -> {
            if (enforcedSchoolId == null) {
                throw new StudentRequestException("School context is required");
            }
            List<Predicate> predicates = new ArrayList<>();

            // Multi-tenancy enforcement: if an enforcedSchoolId is provided, always filter by it
            predicates.add(criteriaBuilder.equal(root.get("schoolId"), enforcedSchoolId));

            if (criteria != null) {
                if (criteria.getSearch() != null && !criteria.getSearch().isBlank()) {
                    String pattern = "%" + escapeLike(criteria.getSearch().toLowerCase().trim()) + "%";
                    String phonePattern = pattern;
                    try {
                        String normalizedPhone = PhoneNumberNormalizer.normalize(criteria.getSearch());
                        phonePattern = "%" + escapeLike(normalizedPhone.toLowerCase()) + "%";
                    } catch (IllegalArgumentException ignored) {
                        // Keep the regular text search for non-phone terms.
                    }
                    predicates.add(criteriaBuilder.or(
                            criteriaBuilder.like(criteriaBuilder.lower(root.get("firstName")), pattern, '\\'),
                            criteriaBuilder.like(criteriaBuilder.lower(root.get("lastName")), pattern, '\\'),
                            criteriaBuilder.like(criteriaBuilder.lower(root.get("middleName")), pattern, '\\'),
                            criteriaBuilder.like(criteriaBuilder.lower(root.get("admissionNumber")), pattern, '\\'),
                            criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), pattern, '\\'),
                            criteriaBuilder.like(criteriaBuilder.lower(root.get("rollNumber")), pattern, '\\'),
                            criteriaBuilder.like(criteriaBuilder.lower(root.get("phone")), phonePattern, '\\')
                    ));
                }

                if (criteria.getGradeLevel() != null && !criteria.getGradeLevel().isBlank()) {
                    predicates.add(criteriaBuilder.equal(
                            criteriaBuilder.lower(root.get("gradeLevel")),
                            criteria.getGradeLevel().toLowerCase().trim()
                    ));
                }

                if (criteria.getSection() != null && !criteria.getSection().isBlank()) {
                    predicates.add(criteriaBuilder.equal(
                            criteriaBuilder.upper(root.get("section")),
                            criteria.getSection().toUpperCase().trim()
                    ));
                }

                if (criteria.getAcademicYear() != null && !criteria.getAcademicYear().isBlank()) {
                    predicates.add(criteriaBuilder.equal(root.get("academicYear"), criteria.getAcademicYear().trim()));
                }

                if (criteria.getStatus() != null) {
                    predicates.add(criteriaBuilder.equal(root.get("status"), criteria.getStatus()));
                }

                if (criteria.getGender() != null) {
                    predicates.add(criteriaBuilder.equal(root.get("gender"), criteria.getGender()));
                }

                if (criteria.getBloodGroup() != null) {
                    predicates.add(criteriaBuilder.equal(root.get("bloodGroup"), criteria.getBloodGroup()));
                }

                if (criteria.getActive() != null) {
                    predicates.add(criteria.getActive()
                            ? criteriaBuilder.equal(root.get("status"), com.peterscode.edutrack.student.enums.StudentStatus.ACTIVE)
                            : criteriaBuilder.notEqual(root.get("status"), com.peterscode.edutrack.student.enums.StudentStatus.ACTIVE));
                }

            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
