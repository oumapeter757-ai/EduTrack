package com.peterscode.edutrack.timetable.repository;

import com.peterscode.edutrack.timetable.entity.ClassLevelSubject;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassLevelSubjectRepository extends JpaRepository<ClassLevelSubject, Long> {
    List<ClassLevelSubject> findBySchoolIdAndClassLevelId(Long schoolId, Long classLevelId);
    Optional<ClassLevelSubject> findBySchoolIdAndClassLevelIdAndSubjectId(Long schoolId, Long classLevelId, Long subjectId);
    boolean existsBySchoolIdAndClassLevelIdAndSubjectId(Long schoolId, Long classLevelId, Long subjectId);
}
