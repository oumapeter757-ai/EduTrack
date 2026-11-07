package com.peterscode.edutrack.lms.repository;

import com.peterscode.edutrack.lms.entity.FileStatus;
import com.peterscode.edutrack.lms.entity.StoredFile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    @Query("SELECT f FROM StoredFile f WHERE f.schoolId = :schoolId AND f.status = 'DELETED' AND f.createdAt < :before")
    List<StoredFile> findDeletedBySchoolBefore(@Param("schoolId") Long schoolId, @Param("before") LocalDateTime before);

    @Query("SELECT f FROM StoredFile f WHERE f.schoolId = :schoolId AND f.createdAt < :before")
    List<StoredFile> findBySchoolBefore(@Param("schoolId") Long schoolId, @Param("before") LocalDateTime before);

    long countBySchoolId(Long schoolId);

    /**
     * Claim a bounded batch of DELETED files for cleanup using SKIP LOCKED.
     * Multiple instances calling this method will get disjoint sets of rows.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Transactional
    @Query("SELECT f FROM StoredFile f WHERE f.status = :status " +
            "AND f.createdAt < :before " +
            "AND (f.claimedBy IS NULL OR f.claimedUntil < :now) " +
            "ORDER BY f.createdAt ASC")
    List<StoredFile> claimDeletedFiles(
            @Param("status") FileStatus status,
            @Param("before") LocalDateTime before,
            @Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE stored_file
            SET claimed_by = :runnerId, claimed_until = :leaseUntil
            WHERE id IN (
                SELECT id FROM (
                    SELECT id FROM stored_file
                    WHERE status = :status
                      AND created_at < :before
                      AND (claimed_by IS NULL OR claimed_until < :now)
                    ORDER BY created_at, id
                    LIMIT :batchSize
                ) candidates
            )
            """, nativeQuery = true)
    int claimDeletedFilesAtomically(@Param("status") String status,
                                    @Param("before") LocalDateTime before,
                                    @Param("now") LocalDateTime now,
                                    @Param("runnerId") String runnerId,
                                    @Param("leaseUntil") LocalDateTime leaseUntil,
                                    @Param("batchSize") int batchSize);

    @Query("SELECT f FROM StoredFile f WHERE f.claimedBy = :runnerId " +
            "AND f.claimedUntil = :leaseUntil ORDER BY f.createdAt ASC")
    List<StoredFile> findClaimedByRunner(@Param("runnerId") String runnerId,
                                         @Param("leaseUntil") LocalDateTime leaseUntil);

    /**
     * Return expired claims back to the queue.
     */
    @Modifying
    @Transactional
    @Query("UPDATE StoredFile f SET f.claimedBy = NULL, f.claimedUntil = NULL " +
            "WHERE f.claimedUntil < :now AND f.status = :status")
    int returnExpiredClaims(
            @Param("now") LocalDateTime now,
            @Param("status") FileStatus status);
}
