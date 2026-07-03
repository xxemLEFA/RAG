package com.repository.repositoryback.dto;

public record RagMatchItem(
        String fileName,
        int chunkIndex,
        String content,
        Double score
) {
}
