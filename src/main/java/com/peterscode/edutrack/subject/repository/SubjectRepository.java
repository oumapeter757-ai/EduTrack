package com.peterscode.edutrack.subject.repository;

import com.peterscode.edutrack.subject.entity.Subject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long>, JpaSpecificationExecutor<Subject> {

    Page<Subject> findBySchoolId(Long schoolId, Pageable pageable);

    List<Subject> findBySchoolIdAndActiveTrueOrderByNameAsc(Long schoolId);

    List<Subject> findBySchoolIdAndClassLevelId(Long schoolId, Long classLevelId);

    List<Subject> findBySchoolIdAndClassLevelIdAndActiveTrue(Long schoolId, Long classLevelId);

    Optional<Subject> findBySchoolIdAndCode(Long schoolId, String code);

    boolean existsBySchoolIdAndCode(Long schoolId, String code);

    boolean existsBySchoolIdAndCodeAndIdNot(Long schoolId, String code, Long id);

    long countBySchoolId(Long schoolId);

    long countBySchoolIdAndClassLevelId(Long schoolId, Long classLevelId);

    Optional<Subject> findBySchoolIdAndId(Long schoolId, Long id);
}
