package com.peterscode.edutrack.lms.repository;

import com.peterscode.edutrack.lms.entity.LmsSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LmsSubmissionRepository extends JpaRepository<LmsSubmission, Long> {

    @Query("SELECT s FROM LmsSubmission s WHERE s.assignmentId = :assignmentId")
    Page<LmsSubmission> findByAssignmentId(@Param("assignmentId") Long assignmentId, Pageable pageable);

    @Query("SELECT s FROM LmsSubmission s WHERE s.schoolId = :schoolId AND s.id = :id")
    Optional<LmsSubmission> findBySchoolIdAndId(@Param("schoolId") Long schoolId, @Param("id") Long id);

    @Query("SELECT s FROM LmsSubmission s WHERE s.schoolId = :schoolId AND s.assignmentId = :assignmentId AND s.studentId = :studentId")
    Optional<LmsSubmission> findBySchoolIdAndAssignmentIdAndStudentId(
            @Param("schoolId") Long schoolId,
            @Param("assignmentId") Long assignmentId,
            @Param("studentId") Long studentId);

    @Query("SELECT s FROM LmsSubmission s WHERE s.studentId = :studentId ORDER BY s.submittedAt DESC")
    Page<LmsSubmission> findByStudentId(@Param("studentId") Long studentId, Pageable pageable);
}
