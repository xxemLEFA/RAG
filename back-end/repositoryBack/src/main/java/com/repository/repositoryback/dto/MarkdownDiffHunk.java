package com.repository.repositoryback.dto;

import java.util.List;

public record MarkdownDiffHunk(
        int sourceStartLine,
        int targetStartLine,
        List<MarkdownDiffLine> lines
) {
}
