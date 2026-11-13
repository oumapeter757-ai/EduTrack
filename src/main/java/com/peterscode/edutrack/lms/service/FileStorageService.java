package com.peterscode.edutrack.lms.service;

import com.peterscode.edutrack.lms.entity.StoredFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface FileStorageService {

    /**
     * Upload a file with full validation.
     */
    StoredFile upload(MultipartFile file, Long schoolId, Long uploadedBy);

    /**
     * Download a file by its stored file id. Returns an input stream.
     */
    InputStream download(Long fileId);

    /**
     * Get metadata for a file without reading the content.
     */
    StoredFile head(Long fileId);

    /**
     * Mark a file as deleted; physical content is removed by the cleanup job.
     */
    void delete(Long fileId);

    /**
     * Get the content type for a stored file.
     */
    String getContentType(Long fileId);

    /**
     * Get the original filename for a stored file.
     */
    String getOriginalName(Long fileId);

    /**
     * Get the size in bytes for a stored file.
     */
    long getSizeBytes(Long fileId);
}
