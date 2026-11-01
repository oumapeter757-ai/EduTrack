package com.peterscode.edutrack.lms.repository;

import com.peterscode.edutrack.lms.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    @Query("SELECT f FROM StoredFile f WHERE f.schoolId = :schoolId AND f.status = 'DELETED' AND f.createdAt < :before")
    List<StoredFile> findDeletedBySchoolBefore(@Param("schoolId") Long schoolId, @Param("before") java.time.LocalDateTime before);

    @Query("SELECT f FROM StoredFile f WHERE f.schoolId = :schoolId AND f.createdAt < :before")
    List<StoredFile> findBySchoolBefore(@Param("schoolId") Long schoolId, @Param("before") java.time.LocalDateTime before);

    long countBySchoolId(Long schoolId);
}
