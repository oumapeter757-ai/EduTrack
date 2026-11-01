package com.peterscode.edutrack.lms.controller;

import com.peterscode.edutrack.lms.entity.SchoolStorageUsage;
import com.peterscode.edutrack.lms.entity.StoredFile;
import com.peterscode.edutrack.lms.exception.LmsRuleException;
import com.peterscode.edutrack.lms.repository.SchoolStorageUsageRepository;
import com.peterscode.edutrack.lms.service.FileStorageService;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.security.RequiresModule;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/api/v1/lms")
@RequiredArgsConstructor
@RequiresModule(ServiceModule.LMS)
public class LmsFileController {

    private final FileStorageService fileStorageService;
    private final SchoolStorageUsageRepository storageUsageRepository;

    @PostMapping(value = "/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<StoredFileView>> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long schoolId = principal.getSchoolId();
        StoredFile storedFile = fileStorageService.upload(file, schoolId, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("File uploaded", new StoredFileView(storedFile)));
    }

    @GetMapping("/files/{fileId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InputStreamResource> download(
            @PathVariable Long fileId,
            @AuthenticationPrincipal UserPrincipal principal) {
        StoredFile storedFile = fileStorageService.head(fileId);
        
        // Verify school ownership
        Long schoolId = principal.getSchoolId();
        if (!storedFile.getSchoolId().equals(schoolId)) {
            throw new LmsRuleException("FILE_NOT_FOUND", HttpStatus.NOT_FOUND, "File not found");
        }

        InputStream inputStream = fileStorageService.download(fileId);
        String contentType = fileStorageService.getContentType(fileId);
        String originalName = fileStorageService.getOriginalName(fileId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + originalName + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(inputStream));
    }

    @RequestMapping(method = org.springframework.web.bind.annotation.RequestMethod.HEAD, value = "/files/{fileId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> head(
            @PathVariable Long fileId,
            @AuthenticationPrincipal UserPrincipal principal) {
        StoredFile storedFile = fileStorageService.head(fileId);
        
        Long schoolId = principal.getSchoolId();
        if (!storedFile.getSchoolId().equals(schoolId)) {
            throw new LmsRuleException("FILE_NOT_FOUND", HttpStatus.NOT_FOUND, "File not found");
        }

        String contentType = fileStorageService.getContentType(fileId);
        String originalName = fileStorageService.getOriginalName(fileId);
        long sizeBytes = fileStorageService.getSizeBytes(fileId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + originalName + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .contentLength(sizeBytes)
                .build();
    }

    @GetMapping("/storage/usage")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<StorageUsageView>> getStorageUsage(
            @AuthenticationPrincipal UserPrincipal principal) {
        Long schoolId = principal.getSchoolId();
        SchoolStorageUsage usage = storageUsageRepository.findBySchoolId(schoolId)
                .orElse(SchoolStorageUsage.builder().schoolId(schoolId).usedBytes(0L).build());
        
        StorageUsageView view = new StorageUsageView(
                usage.getSchoolId(),
                usage.getUsedBytes()
        );
        return ResponseEntity.ok(ApiResponse.success("Storage usage", view));
    }

    public record StoredFileView(
            Long id,
            String originalName,
            String contentType,
            Long sizeBytes,
            String sha256,
            String scanStatus,
            String status
    ) {
        public StoredFileView(StoredFile file) {
            this(
                    file.getId(),
                    file.getOriginalName(),
                    file.getContentType(),
                    file.getSizeBytes(),
                    file.getSha256(),
                    file.getScanStatus().name(),
                    file.getStatus().name()
            );
        }
    }

    public record StorageUsageView(
            Long schoolId,
            Long usedBytes
    ) {
    }
}
