package com.peterscode.edutrack.grading.repository;

import com.peterscode.edutrack.grading.entity.SubjectAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubjectAssessmentRepository extends JpaRepository<SubjectAssessment, Long> {
    List<SubjectAssessment> findBySchoolIdAndTermIdAndSubjectId(Long schoolId, Long termId, Long subjectId);
    List<SubjectAssessment> findBySchoolIdAndTermId(Long schoolId, Long termId);
    boolean existsBySchoolIdAndTermIdAndSubjectIdAndAssessmentTypeId(
            Long schoolId, Long termId, Long subjectId, Long assessmentTypeId);
    Optional<SubjectAssessment> findBySchoolIdAndTermIdAndSubjectIdAndAssessmentTypeId(
            Long schoolId, Long termId, Long subjectId, Long assessmentTypeId);
}
