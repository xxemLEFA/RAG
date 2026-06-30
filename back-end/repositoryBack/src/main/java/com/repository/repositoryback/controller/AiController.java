package com.repository.repositoryback.controller;

import com.repository.repositoryback.dto.AiChatRequest;
import com.repository.repositoryback.dto.AiChatResponse;
import com.repository.repositoryback.dto.AiRagResponse;
import com.repository.repositoryback.dto.KnowledgeOverviewResponse;
import com.repository.repositoryback.service.OllamaService;
import com.repository.repositoryback.service.SimpleRagService;
import com.repository.repositoryback.service.VectorRagService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final OllamaService ollamaService;
    private final SimpleRagService simpleRagService;
    private final VectorRagService vectorRagService;

    public AiController(
            OllamaService ollamaService,
            SimpleRagService simpleRagService,
            VectorRagService vectorRagService
    ) {
        this.ollamaService = ollamaService;
        this.simpleRagService = simpleRagService;
        this.vectorRagService = vectorRagService;
    }

    @PostMapping("/chat")
    public AiChatResponse chat(@Valid @RequestBody AiChatRequest request) {
        return new AiChatResponse(ollamaService.chat(request.question()));
    }

    @PostMapping("/rag-simple")
    public AiRagResponse ragSimple(@Valid @RequestBody AiChatRequest request) {
        return simpleRagService.ask(request.question());
    }

    @PostMapping("/rag")
    public AiRagResponse rag(@Valid @RequestBody AiChatRequest request) {
        return vectorRagService.ask(request.question());
    }

    @GetMapping("/knowledge")
    public KnowledgeOverviewResponse knowledgeOverview() {
        return vectorRagService.inspectKnowledge();
    }

    @PostMapping("/knowledge/reindex")
    public KnowledgeOverviewResponse reindexKnowledge() {
        return vectorRagService.rebuildKnowledgeIndex();
    }
}
