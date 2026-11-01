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

    @Scheduled(fixedRateString = "${edutrack.storage.cleanup-interval-ms:86400000}")
    @Transactional
    public void cleanup() {
        log.info("Starting LMS file cleanup job");
        LocalDateTime cutoff = LocalDateTime.now().minusHours(cleanupGraceHours);
        
        List<StoredFile> deletedFiles = storedFileRepository.findBySchoolBefore(null, cutoff);
        int cleaned = 0;
        
        for (StoredFile file : deletedFiles) {
            if (cleaned >= cleanupBatchSize) break;
            if (file.getStatus() != FileStatus.DELETED) continue;
            
            try {
                String relativePath = file.getSchoolId() + "/" + file.getStorageKey();
                Path filePath = Paths.get(basePath, relativePath);
                Files.deleteIfExists(filePath);
                storedFileRepository.delete(file);
                cleaned++;
            } catch (IOException e) {
                log.warn("Failed to delete file during cleanup: {}", file.getId(), e);
            }
        }
        
        log.info("LMS file cleanup job completed. Cleaned {} files", cleaned);
    }
}
