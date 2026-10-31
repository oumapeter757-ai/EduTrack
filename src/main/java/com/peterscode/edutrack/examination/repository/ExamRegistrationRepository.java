package com.peterscode.edutrack.examination.repository;

import com.peterscode.edutrack.examination.entity.ExamRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamRegistrationRepository extends JpaRepository<ExamRegistration, Long> {
    List<ExamRegistration> findBySchoolIdAndExamSeriesId(Long schoolId, Long examSeriesId);
    List<ExamRegistration> findBySchoolIdAndExamSeriesIdAndStudentId(
            Long schoolId, Long examSeriesId, Long studentId);
    Optional<ExamRegistration> findBySchoolIdAndExamSeriesIdAndStudentIdAndAssessmentPaperId(
            Long schoolId, Long examSeriesId, Long studentId, Long assessmentPaperId);
    Optional<ExamRegistration> findBySchoolIdAndId(Long schoolId, Long id);
    Optional<ExamRegistration> findBySchoolIdAndExamSeriesIdAndCandidateNumber(
            Long schoolId, Long examSeriesId, String candidateNumber);
}
