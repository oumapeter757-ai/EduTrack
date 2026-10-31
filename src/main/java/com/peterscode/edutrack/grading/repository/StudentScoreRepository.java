package com.peterscode.edutrack.grading.repository;

import com.peterscode.edutrack.grading.entity.StudentScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentScoreRepository extends JpaRepository<StudentScore, Long> {
    List<StudentScore> findBySchoolIdAndTermIdAndStreamIdAndSubjectIdAndAssessmentPaperId(
            Long schoolId, Long termId, Long streamId, Long subjectId, Long assessmentPaperId);

    List<StudentScore> findBySchoolIdAndStudentIdAndTermId(Long schoolId, Long studentId, Long termId);

    List<StudentScore> findBySchoolIdAndTermIdAndStreamIdAndSubjectId(
            Long schoolId, Long termId, Long streamId, Long subjectId);

    List<StudentScore> findBySchoolIdAndTermIdAndStreamIdAndSubjectIdAndStudentIdIn(
            Long schoolId, Long termId, Long streamId, Long subjectId, List<Long> studentIds);

}
