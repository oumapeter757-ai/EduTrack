package com.peterscode.edutrack.classlevel.repository;

import com.peterscode.edutrack.classlevel.entity.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface StreamRepository extends JpaRepository<Stream, Long>, JpaSpecificationExecutor<Stream> {

    Page<Stream> findBySchoolId(Long schoolId, Pageable pageable);

    Optional<Stream> findBySchoolIdAndId(Long schoolId, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stream s where s.schoolId = :schoolId and s.id = :streamId")
    Optional<Stream> findLockedBySchoolIdAndId(@Param("schoolId") Long schoolId, @Param("streamId") Long streamId);

    List<Stream> findByClassLevelIdAndActiveTrueOrderByNameAsc(Long classLevelId);

    List<Stream> findBySchoolIdAndClassLevelId(Long schoolId, Long classLevelId);

    Optional<Stream> findByClassLevelIdAndCode(Long classLevelId, String code);

    boolean existsByClassLevelIdAndCode(Long classLevelId, String code);

    boolean existsByClassLevelIdAndCodeAndIdNot(Long classLevelId, String code, Long id);

    long countByClassLevelId(Long classLevelId);

    long countBySchoolId(Long schoolId);
}
