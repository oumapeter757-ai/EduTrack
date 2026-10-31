package com.peterscode.edutrack.timetable.repository;

import com.peterscode.edutrack.timetable.entity.Timetable;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimetableRepository extends JpaRepository<Timetable, Long> {
    Optional<Timetable> findBySchoolIdAndId(Long schoolId, Long id);
    Optional<Timetable> findBySchoolIdAndTermId(Long schoolId, Long termId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Timetable t where t.schoolId = :schoolId and t.id = :id")
    Optional<Timetable> findLocked(@Param("schoolId") Long schoolId, @Param("id") Long id);
}
