package com.peterscode.edutrack.lms.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "edutrack.storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalFileContentStore implements FileContentStore {

    private final Path basePath;

    public LocalFileContentStore(@Value("${edutrack.storage.base-path}") String basePath) {
        this.basePath = Paths.get(basePath).toAbsolutePath().normalize();
    }

    @Override
    public void write(String key, byte[] content, String contentType) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
    }

    @Override
    public Optional<InputStream> open(String key) throws IOException {
        Path target = resolve(key);
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        return Optional.of(Files.newInputStream(target));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    private Path resolve(String key) throws IOException {
        Path target = basePath.resolve(key).normalize();
        if (!target.startsWith(basePath)) {
            throw new IOException("Storage key is outside the configured base path");
        }
        return target;
    }
}
