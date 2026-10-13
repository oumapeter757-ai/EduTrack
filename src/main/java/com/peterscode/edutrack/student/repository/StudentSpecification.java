package com.peterscode.edutrack.student.repository;

import com.peterscode.edutrack.student.dto.StudentSearchCriteria;
import com.peterscode.edutrack.student.entity.Student;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class StudentSpecification {

    private StudentSpecification() {
    }

    public static Specification<Student> withCriteria(StudentSearchCriteria criteria, Long enforcedSchoolId) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Multi-tenancy enforcement: if an enforcedSchoolId is provided, always filter by it
            Long targetSchoolId = enforcedSchoolId != null ? enforcedSchoolId : (criteria != null ? criteria.getSchoolId() : null);
            if (targetSchoolId != null) {
                predicates.add(criteriaBuilder.equal(root.get("schoolId"), targetSchoolId));
            }

            if (criteria != null) {
                if (criteria.getSearch() != null && !criteria.getSearch().isBlank()) {
                    String pattern = "%" + criteria.getSearch().toLowerCase().trim() + "%";
                    predicates.add(criteriaBuilder.or(
                            criteriaBuilder.like(criteriaBuilder.lower(root.get("firstName")), pattern),
                            criteriaBuilder.like(criteriaBuilder.lower(root.get("lastName")), pattern),
                            criteriaBuilder.like(criteriaBuilder.lower(root.get("middleName")), pattern),
                            criteriaBuilder.like(criteriaBuilder.lower(root.get("admissionNumber")), pattern),
                            criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), pattern),
                            criteriaBuilder.like(criteriaBuilder.lower(root.get("rollNumber")), pattern),
                            criteriaBuilder.like(criteriaBuilder.lower(root.get("phone")), pattern)
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
                    predicates.add(criteriaBuilder.equal(root.get("active"), criteria.getActive()));
                }
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
