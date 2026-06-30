package com.repository.repositoryback.dto;

import java.util.List;

public record MarkdownModifiedFileItem(
        String relativePath,
        int sourceLineCount,
        int targetLineCount,
        int additions,
        int deletions,
        List<MarkdownDiffHunk> hunks
) {
}
