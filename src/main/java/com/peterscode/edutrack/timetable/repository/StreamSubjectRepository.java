package com.peterscode.edutrack.timetable.repository;

import com.peterscode.edutrack.timetable.entity.StreamSubject;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StreamSubjectRepository extends JpaRepository<StreamSubject, Long> {
    List<StreamSubject> findBySchoolIdAndStreamIdAndAcademicYearId(Long schoolId, Long streamId, Long academicYearId);
    List<StreamSubject> findBySchoolIdAndAcademicYearId(Long schoolId, Long academicYearId);
    Optional<StreamSubject> findBySchoolIdAndId(Long schoolId, Long id);
    boolean existsBySchoolIdAndStreamIdAndSubjectIdAndAcademicYearId(Long schoolId, Long streamId, Long subjectId, Long academicYearId);
    long countBySchoolIdAndTeacherIdAndAcademicYearId(Long schoolId, Long teacherId, Long academicYearId);

    @Query("select coalesce(sum(s.lessonsPerWeek), 0) from StreamSubject s where s.schoolId = :school and s.teacherId = :teacher and s.academicYearId = :year and (:excludeId is null or s.id <> :excludeId)")
    long sumWeeklyLessons(@Param("school") Long schoolId, @Param("teacher") Long teacherId,
            @Param("year") Long academicYearId, @Param("excludeId") Long excludedId);
}
