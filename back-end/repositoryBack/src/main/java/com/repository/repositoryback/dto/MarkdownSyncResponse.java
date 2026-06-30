package com.repository.repositoryback.dto;

import java.util.List;

public record MarkdownSyncResponse(
        String sourceDir,
        String targetDir,
        String copiedFrom,
        String copiedTo,
        List<String> syncedFiles
) {
}
