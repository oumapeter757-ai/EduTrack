package com.peterscode.edutrack.lms.storage;

import com.peterscode.edutrack.lms.entity.FileStatus;
import com.peterscode.edutrack.lms.entity.StoredFile;
import com.peterscode.edutrack.lms.repository.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Scheduled job that cleans up deleted LMS files from storage.
 * <p>
 * Uses SKIP LOCKED to claim a bounded batch of DELETED files so multiple instances
 * process disjoint rows. File I/O happens outside any DB transaction.
 * </p>
 * <p>
 * Multi-instance safety:
 * 1. Return expired claims back to the queue
 * 2. Claim bounded batch with PESSIMISTIC_WRITE + SKIP LOCKED
 * 3. Mark claimed rows with lease
 * 4. Delete files from disk OUTSIDE transaction
 * 5. Delete DB rows in small batches
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LmsFileCleanupJob {

    private final StoredFileRepository storedFileRepository;

    @Value("${edutrack.storage.base-path}")
    private String basePath;

    @Value("${edutrack.storage.cleanup-grace-hours:24}")
    private int cleanupGraceHours;

    @Value("${edutrack.storage.cleanup-batch-size:100}")
    private int cleanupBatchSize;

    @Value("${edutrack.storage.cleanup-lease-minutes:30}")
    private int leaseMinutes;

    /**
     * Unique identifier for this job instance (set at startup).
     */
    private static final String RUNNER_ID = "lms-cleanup-" + UUID.randomUUID().toString().substring(0, 8);

    @Scheduled(fixedRateString = "${edutrack.storage.cleanup-interval-ms:86400000}")
    public void cleanup() {
        log.info("Starting LMS file cleanup job (runner={})", RUNNER_ID);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusHours(cleanupGraceHours);

        // Return expired claims back to the queue
        int returned = returnExpiredClaims(now);
        if (returned > 0) {
            log.info("LMS cleanup: returned {} expired claims", returned);
        }

        // Claim a bounded batch of DELETED files with SKIP LOCKED
        List<StoredFile> claimed;
        try {
            claimed = claimDeletedFiles(FileStatus.DELETED, cutoff, now);
        } catch (Exception e) {
            log.error("LMS cleanup: failed to claim files", e);
            return;
        }

        if (claimed.isEmpty()) {
            log.info("LMS file cleanup job: no files to clean");
            return;
        }

        log.info("LMS cleanup: claimed {} files for deletion", claimed.size());

        // Delete files from disk OUTSIDE any DB transaction
        List<Long> deletedIds = new java.util.ArrayList<>();
        int cleaned = 0;
        for (StoredFile file : claimed) {
            try {
                String relativePath = file.getSchoolId() + "/" + file.getStorageKey();
                Path filePath = Paths.get(basePath, relativePath);
                Files.deleteIfExists(filePath);
                deletedIds.add(file.getId());
                cleaned++;
            } catch (IOException e) {
                log.warn("Failed to delete file during cleanup: id={}", file.getId(), e);
            }
        }

        // Delete DB rows in a single transaction after file I/O is done
        if (!deletedIds.isEmpty()) {
            deleteByIds(deletedIds);
        }

        log.info("LMS file cleanup job completed. Cleaned {} files", cleaned);
    }

    /**
     * Return expired claims back to the queue.
     */
    @Transactional
    public int returnExpiredClaims(LocalDateTime now) {
        return storedFileRepository.returnExpiredClaims(now, FileStatus.DELETED);
    }

    /**
     * Claim a bounded batch of DELETED files using SKIP LOCKED.
     */
    @Transactional
    public List<StoredFile> claimDeletedFiles(FileStatus status, LocalDateTime before, LocalDateTime now) {
        LocalDateTime leaseUntil = now.plusMinutes(leaseMinutes);
        storedFileRepository.claimDeletedFilesAtomically(status.name(), before, now,
                RUNNER_ID, leaseUntil, cleanupBatchSize);
        return storedFileRepository.findClaimedByRunner(RUNNER_ID, leaseUntil);
    }

    /**
     * Mark claimed rows with this runner's lease.
     */
    @Transactional
    public void markClaimed(List<StoredFile> files, String runnerId, LocalDateTime leaseUntil) {
        for (StoredFile f : files) {
            f.setClaimedBy(runnerId);
            f.setClaimedUntil(leaseUntil);
            storedFileRepository.save(f);
        }
    }

    /**
     * Delete files from the database after they are removed from disk.
     */
    @Transactional
    public void deleteByIds(List<Long> ids) {
        storedFileRepository.deleteAllById(ids);
    }
}
