package com.peterscode.edutrack.timetable.repository;

import com.peterscode.edutrack.timetable.entity.Room;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findBySchoolIdOrderByName(Long schoolId);
    Optional<Room> findBySchoolIdAndId(Long schoolId, Long id);
    boolean existsBySchoolIdAndIdAndActiveTrue(Long schoolId, Long id);
}
