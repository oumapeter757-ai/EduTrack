package com.peterscode.edutrack.classlevel.repository;

import com.peterscode.edutrack.classlevel.entity.ClassLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClassLevelRepository extends JpaRepository<ClassLevel, Long>, JpaSpecificationExecutor<ClassLevel> {

    Page<ClassLevel> findBySchoolId(Long schoolId, Pageable pageable);

    List<ClassLevel> findBySchoolIdAndActiveTrueOrderBySortOrderAsc(Long schoolId);

    Optional<ClassLevel> findBySchoolIdAndCode(Long schoolId, String code);

    boolean existsBySchoolIdAndCode(Long schoolId, String code);

    boolean existsBySchoolIdAndCodeAndIdNot(Long schoolId, String code, Long id);

    long countBySchoolId(Long schoolId);
}
