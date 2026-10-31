package com.peterscode.edutrack.grading.repository;

import com.peterscode.edutrack.grading.entity.AssessmentPaper;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssessmentPaperRepository extends JpaRepository<AssessmentPaper, Long> {
    List<AssessmentPaper> findByAssessmentTypeIdOrderBySortOrder(Long assessmentTypeId);
}
