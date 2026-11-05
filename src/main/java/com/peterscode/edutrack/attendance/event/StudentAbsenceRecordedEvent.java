package com.peterscode.edutrack.attendance.event;

import java.time.LocalDate;
import java.util.List;

public record StudentAbsenceRecordedEvent(Long schoolId, LocalDate attendanceDate,
                                          List<Long> studentIds) {
    public StudentAbsenceRecordedEvent {
        studentIds = studentIds == null ? List.of() : List.copyOf(studentIds);
    }
}
