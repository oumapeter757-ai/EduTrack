package com.peterscode.edutrack.grading.repository;

import com.peterscode.edutrack.grading.entity.GradeScaleRange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GradeScaleRangeRepository extends JpaRepository<GradeScaleRange, Long> {
    List<GradeScaleRange> findByScaleIdOrderBySortOrder(Long scaleId);
}
