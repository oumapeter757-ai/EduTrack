package com.peterscode.edutrack.timetable.repository;

import com.peterscode.edutrack.timetable.entity.TimetableAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimetableAuditRepository extends JpaRepository<TimetableAudit, Long> {
}
