package com.repository.repositoryback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record MarkdownSyncRequest(
        @NotBlank(message = "源目录不能为空")
        String sourceDir,
        @NotBlank(message = "对比目录不能为空")
        String targetDir,
        @NotBlank(message = "同步目标不能为空")
        String destination,
        @NotEmpty(message = "至少选择一个文件")
        List<@NotBlank(message = "文件路径不能为空") String> relativePaths
) {
}
