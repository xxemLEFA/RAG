package com.repository.repositoryback.dto;

import java.util.List;

public record MarkdownCompareResponse(
        String sourceDir,
        String targetDir,
        int sourceFileCount,
        int targetFileCount,
        int unchangedCount,
        List<String> addedFiles,
        List<String> removedFiles,
        List<MarkdownModifiedFileItem> modifiedFiles
) {
}
