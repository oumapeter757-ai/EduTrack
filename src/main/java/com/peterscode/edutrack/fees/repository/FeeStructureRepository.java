package com.peterscode.edutrack.fees.repository;

import com.peterscode.edutrack.fees.entity.FeeStructure;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeeStructureRepository extends JpaRepository<FeeStructure, Long> {

    Optional<FeeStructure> findBySchoolIdAndId(Long schoolId, Long id);

    Page<FeeStructure> findBySchoolId(Long schoolId, Pageable pageable);

    List<FeeStructure> findBySchoolIdAndTermIdAndClassLevelId(Long schoolId, Long termId, Long classLevelId);

    boolean existsBySchoolIdAndTermIdAndClassLevelIdAndName(
            Long schoolId, Long termId, Long classLevelId, String name);
}
