package com.peterscode.edutrack.examination.repository;

import com.peterscode.edutrack.examination.entity.ExamSeries;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamSeriesRepository extends JpaRepository<ExamSeries, Long> {
    Optional<ExamSeries> findBySchoolIdAndId(Long schoolId, Long id);
    List<ExamSeries> findBySchoolId(Long schoolId);
    List<ExamSeries> findBySchoolIdAndTermId(Long schoolId, Long termId);
    List<ExamSeries> findBySchoolIdAndStatus(Long schoolId, ExamSeries.SeriesStatus status);
    boolean existsBySchoolIdAndTermIdAndName(Long schoolId, Long termId, String name);
}
