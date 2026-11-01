package com.peterscode.edutrack.lms.repository;

import com.peterscode.edutrack.lms.entity.LmsAnnouncement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LmsAnnouncementRepository extends JpaRepository<LmsAnnouncement, Long> {

    @Query("SELECT a FROM LmsAnnouncement a WHERE a.courseId = :courseId ORDER BY a.createdAt DESC")
    List<LmsAnnouncement> findByCourseId(@Param("courseId") Long courseId);
}
