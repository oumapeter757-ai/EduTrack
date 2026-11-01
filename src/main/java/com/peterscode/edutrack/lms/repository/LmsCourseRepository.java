package com.peterscode.edutrack.lms.repository;

import com.peterscode.edutrack.lms.entity.LmsCourse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LmsCourseRepository extends JpaRepository<LmsCourse, Long> {

    @Query("SELECT c FROM LmsCourse c WHERE c.schoolId = :schoolId")
    Page<LmsCourse> findBySchoolId(@Param("schoolId") Long schoolId, Pageable pageable);

    @Query("SELECT c FROM LmsCourse c WHERE c.schoolId = :schoolId AND c.id = :id")
    Optional<LmsCourse> findBySchoolIdAndId(@Param("schoolId") Long schoolId, @Param("id") Long id);

    @Query("SELECT c FROM LmsCourse c WHERE c.schoolId = :schoolId AND c.streamSubjectId = :streamSubjectId")
    Optional<LmsCourse> findBySchoolIdAndStreamSubjectId(@Param("schoolId") Long schoolId, @Param("streamSubjectId") Long streamSubjectId);

    boolean existsBySchoolIdAndStreamSubjectId(Long schoolId, Long streamSubjectId);
}
