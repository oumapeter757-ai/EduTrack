package com.peterscode.edutrack.lms.repository;

import com.peterscode.edutrack.lms.entity.LmsTopic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LmsTopicRepository extends JpaRepository<LmsTopic, Long> {

    @Query("SELECT t FROM LmsTopic t WHERE t.courseId = :courseId ORDER BY t.position")
    List<LmsTopic> findByCourseIdOrderByPosition(@Param("courseId") Long courseId);

    @Query("SELECT t FROM LmsTopic t WHERE t.schoolId = :schoolId AND t.id = :id")
    Optional<LmsTopic> findBySchoolIdAndId(@Param("schoolId") Long schoolId, @Param("id") Long id);

    @Query("SELECT MAX(t.position) FROM LmsTopic t WHERE t.courseId = :courseId")
    Optional<Integer> findMaxPositionByCourseId(@Param("courseId") Long courseId);

    long countByCourseId(Long courseId);
}
