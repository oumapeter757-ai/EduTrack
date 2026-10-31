package com.peterscode.edutrack.timetable.repository;

import com.peterscode.edutrack.timetable.entity.Term;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TermRepository extends JpaRepository<Term, Long> {
    Optional<Term> findBySchoolIdAndId(Long schoolId, Long id);
    Optional<Term> findBySchoolIdAndIdAndActiveTrue(Long schoolId, Long id);
    boolean existsBySchoolIdAndAcademicYearIdAndName(Long schoolId, Long academicYearId, String name);
    java.util.List<Term> findBySchoolIdOrderByStartDateAsc(Long schoolId);
    java.util.List<Term> findBySchoolIdAndAcademicYearIdOrderByStartDateAsc(Long schoolId, Long academicYearId);

    @Query("select t from Term t where t.schoolId = :schoolId and t.active = true " +
            "and t.startDate <= :today and t.endDate >= :today order by t.startDate desc")
    Optional<Term> findActiveTermBySchoolId(@Param("schoolId") Long schoolId, @Param("today") LocalDate today);
}
