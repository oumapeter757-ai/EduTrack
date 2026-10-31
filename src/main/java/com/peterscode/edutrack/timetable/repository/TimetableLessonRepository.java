package com.peterscode.edutrack.timetable.repository;

import com.peterscode.edutrack.timetable.entity.TimetableLesson;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimetableLessonRepository extends JpaRepository<TimetableLesson, Long> {
    List<TimetableLesson> findBySchoolIdAndTimetableIdOrderByDayOfWeekAscPeriodIdAsc(Long schoolId, Long timetableId);
    Optional<TimetableLesson> findBySchoolIdAndId(Long schoolId, Long id);
    @Query("""
            select count(l) > 0 from TimetableLesson l
            join Timetable t on t.id = l.timetableId
            where l.schoolId = :schoolId and t.schoolId = :schoolId
              and t.termId = :termId and t.status = com.peterscode.edutrack.timetable.entity.Timetable.TimetableStatus.PUBLISHED
              and l.streamId = :streamId and l.teacherId = :teacherId
            """)
    boolean existsPublishedTeachingAssignment(@Param("schoolId") Long schoolId, @Param("termId") Long termId,
            @Param("streamId") Long streamId, @Param("teacherId") Long teacherId);
    List<TimetableLesson> findBySchoolIdAndStreamSubjectId(Long schoolId, Long streamSubjectId);
    long countBySchoolIdAndTimetableIdAndStreamSubjectId(Long schoolId, Long timetableId, Long streamSubjectId);

    @Query("select count(l) from TimetableLesson l where l.schoolId = :school and l.timetableId = :timetable and l.dayOfWeek = :day and l.periodId = :period and (:exceptId is null or l.id <> :exceptId) and (l.streamId = :stream or l.teacherId = :teacher or (:room is not null and l.roomId = :room))")
    long countSlotConflicts(@Param("school") Long schoolId, @Param("timetable") Long timetableId,
            @Param("day") Integer day, @Param("period") Long periodId, @Param("stream") Long streamId,
            @Param("teacher") Long teacherId, @Param("room") Long roomId, @Param("exceptId") Long exceptId);

    @Query("select count(l) from TimetableLesson l where l.schoolId = :school and l.timetableId = :timetable and l.streamSubjectId = :assignment and l.dayOfWeek = :day and (:exceptId is null or l.id <> :exceptId)")
    long countAssignmentForDay(@Param("school") Long schoolId, @Param("timetable") Long timetableId,
            @Param("assignment") Long assignmentId, @Param("day") Integer day, @Param("exceptId") Long exceptId);

    @Query("select count(l) from TimetableLesson l where l.schoolId = :school and l.timetableId = :timetable and l.teacherId = :teacher and l.dayOfWeek = :day and (:exceptId is null or l.id <> :exceptId)")
    long countTeacherForDay(@Param("school") Long schoolId, @Param("timetable") Long timetableId,
            @Param("teacher") Long teacherId, @Param("day") Integer day, @Param("exceptId") Long exceptId);

    @Query("select count(l) from TimetableLesson l where l.schoolId = :school and l.timetableId = :timetable and l.streamId = :stream and l.dayOfWeek = :day and l.streamSubjectId = :assignment and (:exceptId is null or l.id <> :exceptId)")
    long countSameSubjectForDay(@Param("school") Long schoolId, @Param("timetable") Long timetableId,
            @Param("stream") Long streamId, @Param("assignment") Long assignmentId,
            @Param("day") Integer day, @Param("exceptId") Long exceptId);

    @Query("select l from TimetableLesson l where l.schoolId = :school and l.timetableId = :timetable and l.teacherId = :teacher")
    List<TimetableLesson> findTeacherLessons(@Param("school") Long schoolId, @Param("timetable") Long timetableId,
            @Param("teacher") Long teacherId);

    @Query("select count(l) from TimetableLesson l join Timetable t on t.id = l.timetableId where l.schoolId = :school and t.schoolId = :school and l.periodId = :period and t.status = com.peterscode.edutrack.timetable.entity.Timetable.TimetableStatus.PUBLISHED")
    long countPublishedPeriodUse(@Param("school") Long schoolId, @Param("period") Long periodId);

    @Query("select count(l) from TimetableLesson l join Timetable t on t.id = l.timetableId where l.schoolId = :school and t.schoolId = :school and l.roomId = :room and t.status = com.peterscode.edutrack.timetable.entity.Timetable.TimetableStatus.PUBLISHED")
    long countPublishedRoomUse(@Param("school") Long schoolId, @Param("room") Long roomId);

    @Query("select l from TimetableLesson l join Timetable t on t.id = l.timetableId where l.schoolId = :school and t.schoolId = :school and l.teacherId = :teacher and t.status = com.peterscode.edutrack.timetable.entity.Timetable.TimetableStatus.PUBLISHED")
    List<TimetableLesson> findPublishedTeacherLessons(@Param("school") Long schoolId, @Param("teacher") Long teacherId);

    @Query("select l from TimetableLesson l join Timetable t on t.id = l.timetableId where l.schoolId = :school and t.schoolId = :school and l.streamId = :stream and t.status = com.peterscode.edutrack.timetable.entity.Timetable.TimetableStatus.PUBLISHED")
    List<TimetableLesson> findPublishedStreamLessons(@Param("school") Long schoolId, @Param("stream") Long streamId);
}
