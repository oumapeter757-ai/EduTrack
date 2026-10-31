package com.peterscode.edutrack.timetable.repository;

import com.peterscode.edutrack.timetable.entity.Term;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermRepository extends JpaRepository<Term, Long> {
    Optional<Term> findBySchoolIdAndId(Long schoolId, Long id);
    Optional<Term> findBySchoolIdAndIdAndActiveTrue(Long schoolId, Long id);
    boolean existsBySchoolIdAndAcademicYearIdAndName(Long schoolId, Long academicYearId, String name);
}
