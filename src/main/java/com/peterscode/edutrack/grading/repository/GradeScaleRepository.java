package com.peterscode.edutrack.grading.repository;

import com.peterscode.edutrack.grading.entity.GradeScale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GradeScaleRepository extends JpaRepository<GradeScale, Long> {
    Optional<GradeScale> findBySchoolIdAndId(Long schoolId, Long id);
    List<GradeScale> findBySchoolIdAndActiveTrue(Long schoolId);
    List<GradeScale> findBySchoolIdAndAcademicYearIdAndActiveTrue(Long schoolId, Long academicYearId);
}
