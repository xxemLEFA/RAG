package com.repository.repositoryback.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "knowledge.simple")
public record KnowledgeProperties(
        String baseDir,
        String filePattern,
        int maxFiles,
        int maxCharsPerFile
) {
}
