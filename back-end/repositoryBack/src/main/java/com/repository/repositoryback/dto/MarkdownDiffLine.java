package com.repository.repositoryback.dto;

public record MarkdownDiffLine(
        String type,
        Integer sourceLineNumber,
        Integer targetLineNumber,
        String content
) {
}
