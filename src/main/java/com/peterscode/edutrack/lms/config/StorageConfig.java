package com.peterscode.edutrack.lms.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class StorageConfig {

    @Value("${edutrack.storage.base-path:}")
    private String basePath;

    @Value("${edutrack.storage.max-file-size:10485760}")
    private long maxFileSize;

    @PostConstruct
    public void validateStoragePath() {
        if (basePath == null || basePath.isBlank()) {
            throw new IllegalStateException(
                    "edutrack.storage.base-path must be set explicitly. " +
                    "Set it via application-dev.yaml or environment variable EDUTRACK_STORAGE_PATH.");
        }

        Path path = Paths.get(basePath).toAbsolutePath().normalize();

        // Check for '..'
        if (basePath.contains("..")) {
            throw new IllegalStateException("edutrack.storage.base-path must not contain '..'");
        }

        // Must be absolute
        if (!path.isAbsolute()) {
            throw new IllegalStateException("edutrack.storage.base-path must be an absolute path");
        }

        // Must be outside project directory
        Path projectDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (path.startsWith(projectDir)) {
            throw new IllegalStateException(
                    "edutrack.storage.base-path must be outside the project directory");
        }

        // Must be outside static resources
        Path resourcesDir = projectDir.resolve("src/main/resources/static");
        if (path.startsWith(resourcesDir)) {
            throw new IllegalStateException(
                    "edutrack.storage.base-path must be outside static resources");
        }

        // Create directory if it doesn't exist
        File dir = path.toFile();
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IllegalStateException(
                        "edutrack.storage.base-path cannot be created: " + basePath);
            }
        }

        // Must be writable
        if (!dir.canWrite()) {
            throw new IllegalStateException(
                    "edutrack.storage.base-path is not writable: " + basePath);
        }

        // Check not world-readable (best effort)
        try {
            java.nio.file.attribute.PosixFileAttributes attrs =
                    Files.readAttributes(path, java.nio.file.attribute.PosixFileAttributes.class);
            // Check if other users have read permission
            for (java.nio.file.attribute.PosixFilePermission perm : attrs.permissions()) {
                if (perm == java.nio.file.attribute.PosixFilePermission.OTHERS_READ) {
                    throw new IllegalStateException(
                            "edutrack.storage.base-path must not be world-readable. " +
                            "Set permissions to 700 or 750: " + basePath);
                }
            }
        } catch (UnsupportedOperationException e) {
            // Non-POSIX systems skip this check
        } catch (Exception e) {
            // Other systems skip this check
        }
    }

    @Bean
    public MultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }
}
