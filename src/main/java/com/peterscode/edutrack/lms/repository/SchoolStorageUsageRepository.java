package com.peterscode.edutrack.lms.repository;

import com.peterscode.edutrack.lms.entity.SchoolStorageUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface SchoolStorageUsageRepository extends JpaRepository<SchoolStorageUsage, Long> {

    Optional<SchoolStorageUsage> findBySchoolId(Long schoolId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SchoolStorageUsage s WHERE s.schoolId = :schoolId")
    Optional<SchoolStorageUsage> lockBySchoolId(@Param("schoolId") Long schoolId);
}
