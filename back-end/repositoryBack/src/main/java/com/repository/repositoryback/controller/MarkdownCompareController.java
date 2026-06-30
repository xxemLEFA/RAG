package com.repository.repositoryback.controller;

import com.repository.repositoryback.dto.MarkdownCompareRequest;
import com.repository.repositoryback.dto.MarkdownCompareResponse;
import com.repository.repositoryback.service.MarkdownCompareService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tools/markdown")
public class MarkdownCompareController {

    private final MarkdownCompareService markdownCompareService;

    public MarkdownCompareController(MarkdownCompareService markdownCompareService) {
        this.markdownCompareService = markdownCompareService;
    }

    @PostMapping("/compare")
    public MarkdownCompareResponse compare(@Valid @RequestBody MarkdownCompareRequest request) {
        return markdownCompareService.compareDirectories(request.sourceDir(), request.targetDir());
    }
}
