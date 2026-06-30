package com.repository.repositoryback.dto;

import jakarta.validation.constraints.NotBlank;

public record MarkdownCompareRequest(
        @NotBlank(message = "源目录不能为空")
        String sourceDir,
        @NotBlank(message = "对比目录不能为空")
        String targetDir
) {
}
