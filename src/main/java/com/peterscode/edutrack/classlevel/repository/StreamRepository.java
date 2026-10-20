package com.peterscode.edutrack.classlevel.repository;

import com.peterscode.edutrack.classlevel.entity.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StreamRepository extends JpaRepository<Stream, Long>, JpaSpecificationExecutor<Stream> {

    Page<Stream> findBySchoolId(Long schoolId, Pageable pageable);

    List<Stream> findByClassLevelIdAndActiveTrueOrderByNameAsc(Long classLevelId);

    List<Stream> findBySchoolIdAndClassLevelId(Long schoolId, Long classLevelId);

    Optional<Stream> findByClassLevelIdAndCode(Long classLevelId, String code);

    boolean existsByClassLevelIdAndCode(Long classLevelId, String code);

    boolean existsByClassLevelIdAndCodeAndIdNot(Long classLevelId, String code, Long id);

    long countByClassLevelId(Long classLevelId);

    long countBySchoolId(Long schoolId);
}
