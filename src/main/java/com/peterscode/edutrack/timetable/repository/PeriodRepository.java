package com.peterscode.edutrack.timetable.repository;

import com.peterscode.edutrack.timetable.entity.Period;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PeriodRepository extends JpaRepository<Period, Long> {
    List<Period> findBySchoolIdOrderBySortOrder(Long schoolId);
    Optional<Period> findBySchoolIdAndId(Long schoolId, Long id);
    boolean existsBySchoolIdAndIdAndActiveTrue(Long schoolId, Long id);
}
