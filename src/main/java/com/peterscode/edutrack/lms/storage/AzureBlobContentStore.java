package com.peterscode.edutrack.lms.storage;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "edutrack.storage", name = "provider", havingValue = "azure-blob")
public class AzureBlobContentStore implements FileContentStore {

    private final BlobContainerClient containerClient;

    @Override
    public void write(String key, byte[] content, String contentType) throws IOException {
        try {
            var blob = containerClient.getBlobClient(key);
            blob.upload(new ByteArrayInputStream(content), content.length, true);
            blob.setHttpHeaders(new BlobHttpHeaders().setContentType(contentType));
        } catch (RuntimeException exception) {
            throw new IOException("Azure Blob upload failed", exception);
        }
    }

    @Override
    public Optional<InputStream> open(String key) throws IOException {
        try {
            var blob = containerClient.getBlobClient(key);
            if (!blob.exists()) {
                return Optional.empty();
            }
            return Optional.of(blob.openInputStream());
        } catch (RuntimeException exception) {
            throw new IOException("Azure Blob download failed", exception);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        try {
            containerClient.getBlobClient(key).deleteIfExists();
        } catch (RuntimeException exception) {
            throw new IOException("Azure Blob delete failed", exception);
        }
    }
}
