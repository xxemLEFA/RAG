package com.repository.repositoryback.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qdrant")
public record QdrantProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String collectionName,
        String distance,
        int batchSize,
        int timeoutSeconds
) {
}
