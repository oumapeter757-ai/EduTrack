package com.peterscode.edutrack.lms.repository;

import com.peterscode.edutrack.lms.entity.LmsResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.LocalDateTime;

public interface LmsResourceRepository extends JpaRepository<LmsResource, Long> {

    @Query("SELECT r FROM LmsResource r WHERE r.schoolId = :schoolId " +
            "AND r.topicId = :topicId ORDER BY r.id")
    Page<LmsResource> findBySchoolIdAndTopicId(
            @Param("schoolId") Long schoolId,
            @Param("topicId") Long topicId,
            Pageable pageable);

    @Query("SELECT r FROM LmsResource r WHERE r.schoolId = :schoolId " +
            "AND r.topicId = :topicId AND r.published = true " +
            "AND (r.visibleFrom IS NULL OR r.visibleFrom <= :now) ORDER BY r.id")
    Page<LmsResource> findVisibleBySchoolIdAndTopicId(
            @Param("schoolId") Long schoolId,
            @Param("topicId") Long topicId,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Query("SELECT r FROM LmsResource r WHERE r.schoolId = :schoolId AND r.id = :id")
    Optional<LmsResource> findBySchoolIdAndId(@Param("schoolId") Long schoolId, @Param("id") Long id);
}
