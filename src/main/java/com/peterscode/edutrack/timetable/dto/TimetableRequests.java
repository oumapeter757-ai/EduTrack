package com.peterscode.edutrack.timetable.dto;

import com.peterscode.edutrack.timetable.entity.Period.PeriodType;
import com.peterscode.edutrack.timetable.entity.Room.RoomType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class TimetableRequests {
    private TimetableRequests() {}

    public record TermRequest(@NotNull Long schoolId, @NotNull Long academicYearId, @NotBlank @Size(max = 50) String name,
            @NotNull LocalDate startDate, @NotNull LocalDate endDate) {}

    public record PeriodRequest(@NotNull Long schoolId, @NotBlank @Size(max = 50) String name,
            @NotNull LocalTime startTime, @NotNull LocalTime endTime, @NotNull @Positive Integer sortOrder,
            @NotNull PeriodType type, boolean active) {}

    public record RoomRequest(@NotNull Long schoolId, @NotBlank @Size(max = 100) String name,
            @NotNull RoomType roomType, @NotNull @Positive Integer capacity, boolean active) {}

    public record CurriculumItem(@NotNull Long subjectId, @NotNull @Positive Integer lessonsPerWeek, boolean compulsory) {}
    public record CurriculumRequest(@NotNull Long schoolId, @NotNull Long classLevelId,
            @NotEmpty List<@Valid CurriculumItem> subjects) {}
    public record ApplyCurriculumRequest(@NotNull Long schoolId, @NotNull Long academicYearId,
            @NotEmpty List<@NotNull Long> streamIds) {}

    public record AssignmentItem(@NotNull Long streamId, @NotNull Long subjectId, @NotNull Long academicYearId,
            Long teacherId, @NotNull @Positive Integer lessonsPerWeek) {}
    public record AssignmentBatch(@NotNull Long schoolId, @NotEmpty List<@Valid AssignmentItem> assignments) {}

    public record TimetableRequest(@NotNull Long schoolId, @NotNull Long termId) {}
    public record LessonItem(@NotNull Long streamSubjectId, @NotNull Long periodId, @NotNull @Min(1) @Max(7) Integer dayOfWeek,
            Long roomId) {}
    public record LessonBatch(@NotNull Long schoolId, @NotNull Long timetableId, @NotEmpty List<@Valid LessonItem> lessons) {}
    public record MoveLessonRequest(@NotNull Long periodId, @NotNull @Min(1) @Max(7) Integer dayOfWeek, Long roomId) {}
    public record PublishRequest(boolean acknowledgeIncomplete) {}
}
