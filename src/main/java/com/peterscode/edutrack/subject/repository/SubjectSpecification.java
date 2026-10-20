package com.peterscode.edutrack.subject.repository;

import com.peterscode.edutrack.subject.entity.Subject;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class SubjectSpecification {

    private SubjectSpecification() {
    }

    public static Specification<Subject> withFilters(Long schoolId, Long classLevelId, String department, Boolean active) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (schoolId != null) {
                predicates.add(criteriaBuilder.equal(root.get("schoolId"), schoolId));
            }

            if (classLevelId != null) {
                predicates.add(criteriaBuilder.equal(root.get("classLevelId"), classLevelId));
            }

            if (department != null && !department.isBlank()) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("department")),
                        department.toLowerCase().trim()
                ));
            }

            if (active != null) {
                predicates.add(criteriaBuilder.equal(root.get("active"), active));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
