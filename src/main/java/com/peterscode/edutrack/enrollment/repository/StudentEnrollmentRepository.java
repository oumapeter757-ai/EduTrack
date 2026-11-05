package com.peterscode.edutrack.enrollment.repository;

import com.peterscode.edutrack.enrollment.entity.StudentEnrollment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface StudentEnrollmentRepository extends JpaRepository<StudentEnrollment, Long> {
    Page<StudentEnrollment> findBySchoolIdAndStudentId(Long schoolId, Long studentId, Pageable pageable);
    Optional<StudentEnrollment> findBySchoolIdAndId(Long schoolId, Long id);
    Optional<StudentEnrollment> findBySchoolIdAndStudentIdAndCurrentMarker(Long schoolId, Long studentId, Integer marker);
    boolean existsBySchoolIdAndStudentIdAndAcademicYearIdAndNonVoidedMarker(
            Long schoolId, Long studentId, Long academicYearId, Integer marker);
    List<StudentEnrollment> findBySchoolIdAndAcademicYearIdAndCurrentMarkerAndStatus(
            Long schoolId, Long academicYearId, Integer marker,
            com.peterscode.edutrack.enrollment.entity.EnrollmentStatus status);
    Page<StudentEnrollment> findBySchoolIdAndAcademicYearIdAndClassLevelIdAndStreamIdAndStatus(
            Long schoolId, Long academicYearId, Long classLevelId, Long streamId,
            com.peterscode.edutrack.enrollment.entity.EnrollmentStatus status, Pageable pageable);

    @Modifying
    @Query("update StudentEnrollment e set e.currentMarker = null, e.endDate = :endDate, e.status = :status, e.outcomeReason = :reason " +
           "where e.schoolId = :schoolId and e.studentId = :studentId and e.currentMarker = 1")
    int closeCurrent(@Param("schoolId") Long schoolId, @Param("studentId") Long studentId,
                     @Param("endDate") java.time.LocalDate endDate,
                     @Param("status") com.peterscode.edutrack.enrollment.entity.EnrollmentStatus status,
                     @Param("reason") String reason);
}
