package com.repository.repositoryback.dto;

public record RagSourceItem(
        String fileName,
        String snippet,
        Double score
) {
}
