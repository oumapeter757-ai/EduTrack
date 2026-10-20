package com.peterscode.edutrack.academicyear.repository;

import com.peterscode.edutrack.academicyear.entity.AcademicYear;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long>, JpaSpecificationExecutor<AcademicYear> {

    Optional<AcademicYear> findBySchoolIdAndName(Long schoolId, String name);

    boolean existsBySchoolIdAndName(Long schoolId, String name);

    boolean existsBySchoolIdAndNameAndIdNot(Long schoolId, String name, Long id);

    Optional<AcademicYear> findBySchoolIdAndCurrentTrue(Long schoolId);

    List<AcademicYear> findBySchoolIdOrderByStartDateDesc(Long schoolId);

    Page<AcademicYear> findBySchoolId(Long schoolId, Pageable pageable);

    @Modifying
    @Query("UPDATE AcademicYear a SET a.current = false WHERE a.schoolId = :schoolId AND a.current = true")
    void unsetCurrentForSchool(Long schoolId);
}
