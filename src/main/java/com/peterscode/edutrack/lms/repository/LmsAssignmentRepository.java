package com.peterscode.edutrack.lms.repository;

import com.peterscode.edutrack.lms.entity.LmsAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LmsAssignmentRepository extends JpaRepository<LmsAssignment, Long> {

    @Query("SELECT a FROM LmsAssignment a WHERE a.courseId = :courseId")
    Page<LmsAssignment> findByCourseId(@Param("courseId") Long courseId, Pageable pageable);

    @Query("SELECT a FROM LmsAssignment a WHERE a.schoolId = :schoolId AND a.id = :id")
    Optional<LmsAssignment> findBySchoolIdAndId(@Param("schoolId") Long schoolId, @Param("id") Long id);
}
