package com.peterscode.edutrack.lms.service;

import com.peterscode.edutrack.lms.audit.LmsAuditService;
import com.peterscode.edutrack.lms.entity.FileStatus;
import com.peterscode.edutrack.lms.entity.ScanStatus;
import com.peterscode.edutrack.lms.entity.SchoolStorageUsage;
import com.peterscode.edutrack.lms.entity.StoredFile;
import com.peterscode.edutrack.lms.exception.LmsRuleException;
import com.peterscode.edutrack.lms.repository.SchoolStorageUsageRepository;
import com.peterscode.edutrack.lms.repository.StoredFileRepository;
import com.peterscode.edutrack.lms.storage.FileScanner;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LocalFileStorageService implements FileStorageService {

    private final StoredFileRepository storedFileRepository;
    private final SchoolStorageUsageRepository storageUsageRepository;
    private final LmsAuditService auditService;
    private final FileScanner fileScanner;

    @Value("${edutrack.storage.base-path}")
    private String basePath;

    @Value("${edutrack.storage.max-file-size:10485760}")
    private long maxFileSize;

    @Value("${edutrack.storage.school-quota-bytes:1073741824}")
    private long schoolQuotaBytes;

    @Value("${edutrack.storage.allowed-extensions:pdf,doc,docx,ppt,pptx,xls,xlsx,txt,png,jpg,jpeg}")
    private String allowedExtensionsStr;

    @Value("${edutrack.storage.min-free-bytes:104857600}")
    private long minFreeBytes;

    private List<String> allowedExtensions;

    private static final Map<String, List<byte[]>> MAGIC_NUMBERS;
    private static final Map<String, String> EXTENSION_TO_CONTENT_TYPE;

    static {
        MAGIC_NUMBERS = new HashMap<>();
        MAGIC_NUMBERS.put("application/pdf", List.of(new byte[]{0x25, 0x50, 0x44, 0x46}));
        MAGIC_NUMBERS.put("image/png", List.of(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}));
        MAGIC_NUMBERS.put("image/jpeg", List.of(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}));
        MAGIC_NUMBERS.put("application/zip", List.of(new byte[]{0x50, (byte) 0x4B, 0x03, 0x04}));
        MAGIC_NUMBERS.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                List.of(new byte[]{0x50, (byte) 0x4B, 0x03, 0x04}));
        MAGIC_NUMBERS.put("application/vnd.openxmlformats-officedocument.presentationml.presentation",
                List.of(new byte[]{0x50, (byte) 0x4B, 0x03, 0x04}));
        MAGIC_NUMBERS.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                List.of(new byte[]{0x50, (byte) 0x4B, 0x03, 0x04}));
        MAGIC_NUMBERS.put("application/msword", List.of(new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1}));
        MAGIC_NUMBERS.put("application/vnd.ms-powerpoint", List.of(new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1}));
        MAGIC_NUMBERS.put("application/vnd.ms-excel", List.of(new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1}));
        MAGIC_NUMBERS.put("text/plain", List.of());

        EXTENSION_TO_CONTENT_TYPE = new HashMap<>();
        EXTENSION_TO_CONTENT_TYPE.put("pdf", "application/pdf");
        EXTENSION_TO_CONTENT_TYPE.put("doc", "application/msword");
        EXTENSION_TO_CONTENT_TYPE.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        EXTENSION_TO_CONTENT_TYPE.put("ppt", "application/vnd.ms-powerpoint");
        EXTENSION_TO_CONTENT_TYPE.put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        EXTENSION_TO_CONTENT_TYPE.put("xls", "application/vnd.ms-excel");
        EXTENSION_TO_CONTENT_TYPE.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        EXTENSION_TO_CONTENT_TYPE.put("txt", "text/plain");
        EXTENSION_TO_CONTENT_TYPE.put("png", "image/png");
        EXTENSION_TO_CONTENT_TYPE.put("jpg", "image/jpeg");
        EXTENSION_TO_CONTENT_TYPE.put("jpeg", "image/jpeg");
    }

    private List<String> getAllowedExtensions() {
        if (allowedExtensions == null) {
            allowedExtensions = Arrays.stream(allowedExtensionsStr.split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());
        }
        return allowedExtensions;
    }

    @Override
    @Transactional
    public StoredFile upload(MultipartFile file, Long schoolId, Long uploadedBy) {
        // Validate file is not empty
        if (file == null || file.isEmpty()) {
            throw new LmsRuleException("EMPTY_FILE", HttpStatus.BAD_REQUEST, "File must not be empty");
        }

        // Validate filename
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new LmsRuleException("INVALID_FILENAME", HttpStatus.BAD_REQUEST, "File must have a name");
        }

        // Check for path traversal
        String sanitized = sanitizeFilename(originalFilename);

        // Check extension
        String extension = extractExtension(sanitized);
        if (extension == null || !getAllowedExtensions().contains(extension.toLowerCase())) {
            throw new LmsRuleException("DISALLOWED_FILE_TYPE", HttpStatus.BAD_REQUEST,
                    "File type '" + extension + "' is not allowed");
        }

        // Check for double extensions
        if (hasDoubleExtension(sanitized)) {
            throw new LmsRuleException("DOUBLE_EXTENSION", HttpStatus.BAD_REQUEST,
                    "Double file extensions are not allowed");
        }

        // Validate size
        if (file.getSize() > maxFileSize) {
            throw new LmsRuleException("FILE_TOO_LARGE", HttpStatus.valueOf(413),
                    "File size exceeds maximum allowed size of " + maxFileSize + " bytes");
        }

        // Check disk space
        File base = new File(basePath);
        if (base.exists() && file.getSize() > base.getFreeSpace() - minFreeBytes) {
            throw new LmsRuleException("INSUFFICIENT_DISK_SPACE", HttpStatus.INTERNAL_SERVER_ERROR,
                    "Insufficient disk space for upload");
        }

        // Validate content type by magic numbers
        String expectedContentType = EXTENSION_TO_CONTENT_TYPE.get(extension.toLowerCase());
        if (expectedContentType == null) {
            throw new LmsRuleException("UNKNOWN_CONTENT_TYPE", HttpStatus.BAD_REQUEST,
                    "Content type for extension '" + extension + "' is not recognized");
        }

        try {
            byte[] firstBytes = file.getBytes();
            if (firstBytes.length == 0) {
                throw new LmsRuleException("EMPTY_FILE", HttpStatus.BAD_REQUEST, "File must not be empty");
            }
            validateMagicNumbers(firstBytes, expectedContentType);
        } catch (IOException e) {
            throw new LmsRuleException("FILE_READ_ERROR", HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to read file content");
        }

        // Check and update storage quota
        SchoolStorageUsage usage = storageUsageRepository.lockBySchoolId(schoolId)
                .orElseGet(() -> {
                    SchoolStorageUsage newUsage = SchoolStorageUsage.builder()
                            .schoolId(schoolId)
                            .usedBytes(0L)
                            .build();
                    return storageUsageRepository.save(newUsage);
                });

        if (usage.getUsedBytes() + file.getSize() > schoolQuotaBytes) {
            throw new LmsRuleException("QUOTA_EXCEEDED", HttpStatus.BAD_REQUEST,
                    "School storage quota exceeded");
        }

        // Generate storage key
        String storageKey = UUID.randomUUID().toString();
        String relativePath = schoolId + "/" + storageKey;
        Path targetPath = Paths.get(basePath, relativePath);

        // Ensure directory exists
        try {
            Files.createDirectories(targetPath.getParent());
        } catch (IOException e) {
            throw new LmsRuleException("STORAGE_ERROR", HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create storage directory");
        }

        // Write file
        try {
            file.transferTo(targetPath.toFile());
        } catch (IOException e) {
            throw new LmsRuleException("STORAGE_ERROR", HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to save file");
        }

        // Compute SHA-256
        String sha256;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(targetPath)) {
                byte[] hash = digest.digest(is.readAllBytes());
                sha256 = bytesToHex(hash);
            }
        } catch (Exception e) {
            // Clean up file on error
            try {
                Files.deleteIfExists(targetPath);
            } catch (IOException ignored) {}
            throw new LmsRuleException("CHECKSUM_ERROR", HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to compute file checksum");
        }

        // Scan file
        String scanStatusStr = ScanStatus.UNSCANNED.name();
        try {
            try (InputStream is = Files.newInputStream(targetPath)) {
                scanStatusStr = fileScanner.scan(is);
            }
        } catch (Exception e) {
            // If scanner fails, keep UNSCANNED
            scanStatusStr = ScanStatus.UNSCANNED.name();
        }

        // Create stored file record
        StoredFile storedFile = StoredFile.builder()
                .schoolId(schoolId)
                .uploadedBy(uploadedBy)
                .originalName(sanitized)
                .contentType(expectedContentType)
                .sizeBytes(file.getSize())
                .storageKey(storageKey)
                .sha256(sha256)
                .scanStatus(ScanStatus.valueOf(scanStatusStr))
                .status(FileStatus.ACTIVE)
                .build();

        storedFile = storedFileRepository.save(storedFile);

        // Update storage usage
        usage.setUsedBytes(usage.getUsedBytes() + file.getSize());
        storageUsageRepository.save(usage);

        // Audit
        auditService.append(schoolId, "STORED_FILE", storedFile.getId(), "UPLOAD",
                uploadedBy, null,
                "file:" + sanitized + ",size:" + file.getSize() + ",sha256:" + sha256,
                null);

        return storedFile;
    }

    @Override
    @Transactional(readOnly = true)
    public InputStream download(Long fileId) {
        StoredFile storedFile = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new LmsRuleException("FILE_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "File not found"));

        if (storedFile.getStatus() == FileStatus.DELETED) {
            throw new LmsRuleException("FILE_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "File not found");
        }

        if (storedFile.getScanStatus() == ScanStatus.REJECTED) {
            throw new LmsRuleException("FILE_REJECTED", HttpStatus.FORBIDDEN,
                    "File has been rejected by security scan");
        }

        String relativePath = storedFile.getSchoolId() + "/" + storedFile.getStorageKey();
        Path filePath = Paths.get(basePath, relativePath);

        if (!Files.exists(filePath)) {
            throw new LmsRuleException("FILE_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "File not found on disk");
        }

        try {
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            throw new LmsRuleException("FILE_READ_ERROR", HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to read file");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public StoredFile head(Long fileId) {
        return storedFileRepository.findById(fileId)
                .orElseThrow(() -> new LmsRuleException("FILE_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "File not found"));
    }

    @Override
    @Transactional
    public void delete(Long fileId) {
        StoredFile storedFile = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new LmsRuleException("FILE_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "File not found"));

        // Mark as deleted
        storedFile.setStatus(FileStatus.DELETED);
        storedFileRepository.save(storedFile);

        // Update storage usage
        SchoolStorageUsage usage = storageUsageRepository.findBySchoolId(storedFile.getSchoolId())
                .orElse(null);
        if (usage != null) {
            long newUsage = Math.max(0, usage.getUsedBytes() - storedFile.getSizeBytes());
            usage.setUsedBytes(newUsage);
            storageUsageRepository.save(usage);
        }

        // Audit
        auditService.append(storedFile.getSchoolId(), "STORED_FILE", fileId, "DELETE",
                storedFile.getUploadedBy(),
                "file:" + storedFile.getOriginalName(),
                "status:DELETED", null);
    }

    @Override
    public String getContentType(Long fileId) {
        return head(fileId).getContentType();
    }

    @Override
    public String getOriginalName(Long fileId) {
        return head(fileId).getOriginalName();
    }

    @Override
    public long getSizeBytes(Long fileId) {
        return head(fileId).getSizeBytes();
    }

    private String sanitizeFilename(String filename) {
        // Reject null bytes
        if (filename.contains("\0")) {
            throw new LmsRuleException("INVALID_FILENAME", HttpStatus.BAD_REQUEST,
                    "Filename contains invalid characters");
        }
        // Reject path separators
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new LmsRuleException("INVALID_FILENAME", HttpStatus.BAD_REQUEST,
                    "Filename contains path traversal sequences");
        }
        // Truncate to 255 chars
        if (filename.length() > 255) {
            filename = filename.substring(0, 255);
        }
        return filename;
    }

    private String extractExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == filename.length() - 1) {
            return null;
        }
        return filename.substring(lastDot + 1);
    }

    private boolean hasDoubleExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot <= 0) return false;
        String namePart = filename.substring(0, lastDot);
        int prevDot = namePart.lastIndexOf('.');
        return prevDot > 0;
    }

    private void validateMagicNumbers(byte[] content, String expectedContentType) {
        List<byte[]> magicPatterns = MAGIC_NUMBERS.get(expectedContentType);
        if (magicPatterns == null || magicPatterns.isEmpty()) {
            // No magic number check for this type (e.g., text/plain)
            return;
        }
        boolean matches = false;
        for (byte[] pattern : magicPatterns) {
            if (pattern.length == 0) continue;
            if (content.length < pattern.length) continue;
            boolean match = true;
            for (int i = 0; i < pattern.length; i++) {
                if (content[i] != pattern[i]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                matches = true;
                break;
            }
        }
        if (!matches) {
            throw new LmsRuleException("CONTENT_MISMATCH", HttpStatus.BAD_REQUEST,
                    "File content does not match the expected type");
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
