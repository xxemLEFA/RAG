package com.repository.repositoryback.dto;

public record QdrantCollectionStatus(
        boolean enabled,
        String baseUrl,
        String collectionName,
        boolean collectionExists,
        Integer indexedPoints,
        String statusMessage
) {
}
