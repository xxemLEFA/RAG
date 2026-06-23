package com.repository.repositoryback.dto;

import java.util.List;

public record AiRagResponse(
        String answer,
        List<RagSourceItem> sources,
        boolean knowledgeHit
) {
}
