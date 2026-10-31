package com.peterscode.edutrack.timetable.dto;

import com.peterscode.edutrack.timetable.entity.Period.PeriodType;
import com.peterscode.edutrack.timetable.entity.Room.RoomType;
import com.peterscode.edutrack.timetable.entity.Timetable.TimetableStatus;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class TimetableViews {
    private TimetableViews() {}

    public record TermView(Long id, Long academicYearId, String name, java.time.LocalDate startDate, java.time.LocalDate endDate) {}
    public record PeriodView(Long id, String name, LocalTime startTime, LocalTime endTime, Integer sortOrder,
            PeriodType type, boolean active) {}
    public record RoomView(Long id, String name, RoomType roomType, Integer capacity, boolean active) {}
    public record CurriculumView(Long id, Long classLevelId, Long subjectId, Integer lessonsPerWeek, boolean compulsory) {}
    public record AssignmentView(Long id, Long streamId, Long subjectId, Long academicYearId, Long teacherId, Integer lessonsPerWeek) {}
    public record TimetableView(Long id, Long termId, TimetableStatus status, LocalDateTime publishedAt, Long version) {}
    public record LessonView(Long id, Long streamSubjectId, Long streamId, Long teacherId, Long roomId, Long periodId,
            Integer dayOfWeek, String subjectName, String teacherDisplayName, String roomName, LocalTime startTime, LocalTime endTime) {}
    public record StudentLessonView(String subject, String teacher, String room, LocalTime startTime, LocalTime endTime, Integer dayOfWeek) {}
    public record TeacherLessonView(String subject, String stream, String room, LocalTime startTime, LocalTime endTime, Integer dayOfWeek) {}
    public record MissingLesson(Long streamId, String streamName, Long subjectId, String subjectName, int expected, long scheduled) {}
    public record CompletenessView(boolean complete, List<MissingLesson> missing) {}
    public record PublishView(TimetableView timetable, CompletenessView completeness) {}
    public record LessonBatchView(List<LessonView> lessons, List<String> warnings) {}
}
