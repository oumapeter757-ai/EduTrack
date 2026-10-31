package com.peterscode.edutrack.grading.repository;

import com.peterscode.edutrack.grading.entity.TermGrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TermGradeRepository extends JpaRepository<TermGrade, Long> {
    List<TermGrade> findBySchoolIdAndTermIdAndStreamIdAndSubjectId(
            Long schoolId, Long termId, Long streamId, Long subjectId);

    List<TermGrade> findBySchoolIdAndStudentIdAndTermId(Long schoolId, Long studentId, Long termId);

    List<TermGrade> findBySchoolIdAndTermId(Long schoolId, Long termId);

    void deleteBySchoolIdAndTermIdAndStreamIdAndSubjectId(
            Long schoolId, Long termId, Long streamId, Long subjectId);
}
