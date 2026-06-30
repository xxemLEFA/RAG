package com.repository.repositoryback.dto;

public record KnowledgeFileItem(
        String fileName,
        long sizeBytes,
        String lastModified,
        boolean usedBySimpleRag,
        boolean usedByVectorRag,
        int chunkCount
) {
}
