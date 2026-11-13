package com.peterscode.edutrack.lms.storage;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "edutrack.storage", name = "provider", havingValue = "azure-blob")
public class AzureBlobStorageConfig {

    @Bean
    public BlobContainerClient blobContainerClient(
            @Value("${edutrack.storage.azure.endpoint}") String endpoint,
            @Value("${edutrack.storage.azure.container}") String container,
            @Value("${edutrack.storage.azure.sas-token}") String sasToken) {
        if (sasToken == null || sasToken.isBlank()) {
            throw new IllegalStateException("Azure Blob storage requires a container SAS token");
        }
        return new BlobServiceClientBuilder()
                .endpoint(endpoint)
                .sasToken(sasToken)
                .buildClient()
                .getBlobContainerClient(container);
    }
}
