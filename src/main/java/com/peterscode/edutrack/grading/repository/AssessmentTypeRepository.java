package com.peterscode.edutrack.grading.repository;

import com.peterscode.edutrack.grading.entity.AssessmentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssessmentTypeRepository extends JpaRepository<AssessmentType, Long> {
    Optional<AssessmentType> findBySchoolIdAndId(Long schoolId, Long id);
    List<AssessmentType> findBySchoolIdAndActiveTrueOrderBySortOrder(Long schoolId);
}
