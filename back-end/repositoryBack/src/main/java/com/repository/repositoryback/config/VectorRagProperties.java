package com.repository.repositoryback.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "knowledge.vector")
public record VectorRagProperties(
        int topK,
        int chunkSize,
        int chunkOverlap,
        int maxFiles,
        int maxChunks
) {
}
