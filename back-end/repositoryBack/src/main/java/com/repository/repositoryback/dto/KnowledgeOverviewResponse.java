package com.repository.repositoryback.dto;

import java.util.List;

public record KnowledgeOverviewResponse(
        String baseDir,
        String filePattern,
        int simpleFileLimit,
        int vectorFileLimit,
        int vectorChunkLimit,
        int totalMatchedFiles,
        int totalVectorChunks,
        List<KnowledgeFileItem> files
) {
}
