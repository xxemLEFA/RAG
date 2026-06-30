package com.repository.repositoryback.dto;

public record DirectoryBrowseRequest(
        String initialDir,
        String dialogTitle
) {
}
