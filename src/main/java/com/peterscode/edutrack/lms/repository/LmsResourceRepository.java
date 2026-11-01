package com.peterscode.edutrack.lms.repository;

import com.peterscode.edutrack.lms.entity.LmsResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LmsResourceRepository extends JpaRepository<LmsResource, Long> {

    @Query("SELECT r FROM LmsResource r WHERE r.topicId = :topicId ORDER BY r.id")
    Page<LmsResource> findByTopicId(@Param("topicId") Long topicId, Pageable pageable);

    @Query("SELECT r FROM LmsResource r WHERE r.schoolId = :schoolId AND r.id = :id")
    Optional<LmsResource> findBySchoolIdAndId(@Param("schoolId") Long schoolId, @Param("id") Long id);
}
