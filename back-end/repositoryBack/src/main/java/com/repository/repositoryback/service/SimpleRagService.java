package com.repository.repositoryback.service;

import com.repository.repositoryback.config.KnowledgeProperties;
import com.repository.repositoryback.dto.AiRagResponse;
import com.repository.repositoryback.dto.RagSourceItem;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
public class SimpleRagService {

    private static final String NO_KNOWLEDGE_ANSWER = "资料中未找到相关信息。";

    private final KnowledgeProperties knowledgeProperties;
    private final OllamaService ollamaService;

    public SimpleRagService(KnowledgeProperties knowledgeProperties, OllamaService ollamaService) {
        this.knowledgeProperties = knowledgeProperties;
        this.ollamaService = ollamaService;
    }

    public AiRagResponse ask(String question) {
        String trimmedQuestion = question == null ? "" : question.trim();
        if (trimmedQuestion.isEmpty()) {
            throw new IllegalArgumentException("question 不能为空");
        }

        List<KnowledgeDocument> documents = loadKnowledgeDocuments();
        if (documents.isEmpty()) {
            throw new RuntimeException("知识库目录中没有可用的 Markdown 文档");
        }

        String prompt = buildPrompt(trimmedQuestion, documents);
        String answer = ollamaService.chatWithSystemPrompt(prompt, buildSystemPrompt());

        List<RagSourceItem> sources = documents.stream()
                .map(document -> new RagSourceItem(document.fileName(), document.snippet(), null))
                .toList();

        boolean knowledgeHit = !answer.contains(NO_KNOWLEDGE_ANSWER);
        return new AiRagResponse(answer, sources, knowledgeHit);
    }

    private List<KnowledgeDocument> loadKnowledgeDocuments() {
        Path basePath = Path.of(knowledgeProperties.baseDir());
        if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
            throw new RuntimeException("知识库目录不存在: " + basePath);
        }

        try (Stream<Path> pathStream = Files.walk(basePath)) {
            return pathStream
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesPattern(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> toRelativePath(basePath, path), String.CASE_INSENSITIVE_ORDER))
                    .limit(Math.max(1, knowledgeProperties.maxFiles()))
                    .map(path -> readKnowledgeDocument(basePath, path))
                    .toList();
        } catch (IOException exception) {
            throw new RuntimeException("读取知识库文档失败: " + exception.getMessage(), exception);
        }
    }

    private boolean matchesPattern(String fileName) {
        String pattern = knowledgeProperties.filePattern();
        if (pattern == null || pattern.isBlank() || "*".equals(pattern) || "*.*".equals(pattern)) {
            return true;
        }
        if ("*.md".equalsIgnoreCase(pattern)) {
            return fileName.toLowerCase(Locale.ROOT).endsWith(".md");
        }
        return fileName.equalsIgnoreCase(pattern);
    }

    private KnowledgeDocument readKnowledgeDocument(Path basePath, Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (content.isEmpty()) {
                throw new RuntimeException("知识库文档为空: " + path.getFileName());
            }

            String normalized = content.replace("\r\n", "\n");
            String excerpt = limitLength(normalized, knowledgeProperties.maxCharsPerFile());
            String snippet = limitLength(normalized.replace('\n', ' '), 280);
            return new KnowledgeDocument(toRelativePath(basePath, path), excerpt, snippet);
        } catch (IOException exception) {
            throw new RuntimeException("读取知识库文档失败: " + path.getFileName(), exception);
        }
    }

    private String toRelativePath(Path basePath, Path path) {
        return basePath.relativize(path).toString().replace('\\', '/');
    }

    private String buildSystemPrompt() {
        return """
                你是一个项目知识库助手。
                请严格根据用户提供的资料回答。
                如果资料中没有答案，请明确回答：资料中未找到相关信息。
                不要根据常识扩展，不要编造资料中没有出现的字段、接口、路径。
                回答末尾请单独列出“来源文件：”并写出使用到的文件名。
                """.trim();
    }

    private String buildPrompt(String question, List<KnowledgeDocument> documents) {
        StringBuilder builder = new StringBuilder();
        builder.append("【知识库资料】\n");

        List<String> fileNames = new ArrayList<>();
        for (KnowledgeDocument document : documents) {
            fileNames.add(document.fileName());
            builder.append("来源文件：").append(document.fileName()).append('\n');
            builder.append("内容：\n").append(document.content()).append("\n\n");
        }

        builder.append("【使用规则】\n")
                .append("1. 只能使用上面的资料内容回答。\n")
                .append("2. 如果资料中没有答案，请回答：").append(NO_KNOWLEDGE_ANSWER).append('\n')
                .append("3. 不要混入资料之外的信息。\n")
                .append("4. 回答最后列出来源文件。\n\n")
                .append("【当前知识库文件】\n")
                .append(String.join("、", fileNames))
                .append("\n\n【用户问题】\n")
                .append(question);

        return builder.toString();
    }

    private String limitLength(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "\n...[内容已截断]";
    }

    private record KnowledgeDocument(String fileName, String content, String snippet) {
    }
}
