package com.peterscode.edutrack.guardian.repository;

import com.peterscode.edutrack.guardian.entity.ParentStudentMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParentStudentMappingRepository extends JpaRepository<ParentStudentMapping, Long> {

    Optional<ParentStudentMapping> findBySchoolIdAndId(Long schoolId, Long id);

    List<ParentStudentMapping> findBySchoolIdAndParentIdAndActiveTrue(Long schoolId, Long parentId);

    List<ParentStudentMapping> findBySchoolIdAndStudentIdAndActiveTrue(Long schoolId, Long studentId);

    Optional<ParentStudentMapping> findBySchoolIdAndParentIdAndStudentId(
        Long schoolId, Long parentId, Long studentId);

    boolean existsBySchoolIdAndParentIdAndActiveTrue(Long schoolId, Long parentId);
}
