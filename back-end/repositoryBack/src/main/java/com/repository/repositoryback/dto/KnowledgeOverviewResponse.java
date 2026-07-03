package com.repository.repositoryback.dto;

import java.util.List;

public record KnowledgeOverviewResponse(
        String baseDir,
        String filePattern,
        String vectorBackend,
        int simpleFileLimit,
        int vectorFileLimit,
        int vectorChunkLimit,
        int totalMatchedFiles,
        int totalVectorChunks,
        int effectiveVectorChunks,
        boolean vectorChunkLimitReached,
        QdrantCollectionStatus qdrantStatus,
        List<KnowledgeFileItem> files
) {
}
