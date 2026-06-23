package com.repository.repositoryback.service;

import com.repository.repositoryback.config.KnowledgeProperties;
import com.repository.repositoryback.config.VectorRagProperties;
import com.repository.repositoryback.dto.AiRagResponse;
import com.repository.repositoryback.dto.RagSourceItem;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Service
public class VectorRagService {

    private static final String NO_KNOWLEDGE_ANSWER = "资料中未找到相关信息。";

    private final KnowledgeProperties knowledgeProperties;
    private final VectorRagProperties vectorRagProperties;
    private final OllamaService ollamaService;
    private final Map<String, CachedDocumentEmbeddings> documentCache = new ConcurrentHashMap<>();

    public VectorRagService(
            KnowledgeProperties knowledgeProperties,
            VectorRagProperties vectorRagProperties,
            OllamaService ollamaService
    ) {
        this.knowledgeProperties = knowledgeProperties;
        this.vectorRagProperties = vectorRagProperties;
        this.ollamaService = ollamaService;
    }

    public AiRagResponse ask(String question) {
        String trimmedQuestion = question == null ? "" : question.trim();
        if (trimmedQuestion.isEmpty()) {
            throw new IllegalArgumentException("question 不能为空");
        }

        List<ChunkEmbedding> candidates = loadChunkEmbeddings();
        if (candidates.isEmpty()) {
            throw new RuntimeException("知识库目录中没有可检索的文档片段");
        }

        List<Double> questionVector = ollamaService.embed(trimmedQuestion);
        List<ChunkMatch> matches = candidates.stream()
                .map(candidate -> new ChunkMatch(candidate, cosineSimilarity(questionVector, candidate.embedding())))
                .sorted(Comparator.comparingDouble(ChunkMatch::score).reversed())
                .limit(Math.max(1, vectorRagProperties.topK()))
                .toList();

        String prompt = buildPrompt(trimmedQuestion, matches);
        String answer = ollamaService.chatWithSystemPrompt(prompt, buildSystemPrompt());

        Map<String, RagSourceItem> sourceMap = new LinkedHashMap<>();
        for (ChunkMatch match : matches) {
            sourceMap.putIfAbsent(
                    sourceKey(match),
                    new RagSourceItem(
                            match.candidate().chunk().fileName(),
                            limitLength(match.candidate().chunk().content().replace('\n', ' '), 320),
                            roundScore(match.score())
                    )
            );
        }

        boolean knowledgeHit = !answer.contains(NO_KNOWLEDGE_ANSWER);
        return new AiRagResponse(answer, new ArrayList<>(sourceMap.values()), knowledgeHit);
    }

    private List<ChunkEmbedding> loadChunkEmbeddings() {
        Path basePath = Path.of(knowledgeProperties.baseDir());
        if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
            throw new RuntimeException("知识库目录不存在: " + basePath);
        }

        try (Stream<Path> pathStream = Files.list(basePath)) {
            return pathStream
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesPattern(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .limit(Math.max(1, vectorRagProperties.maxFiles()))
                    .map(this::loadOrBuildDocumentEmbeddings)
                    .flatMap(cached -> cached.chunks().stream())
                    .limit(Math.max(1, vectorRagProperties.maxChunks()))
                    .toList();
        } catch (IOException exception) {
            throw new RuntimeException("读取知识库文档失败: " + exception.getMessage(), exception);
        }
    }

    private CachedDocumentEmbeddings loadOrBuildDocumentEmbeddings(Path path) {
        try {
            long lastModified = Files.getLastModifiedTime(path).toMillis();
            String cacheKey = path.toAbsolutePath().normalize().toString();
            CachedDocumentEmbeddings cached = documentCache.get(cacheKey);
            if (cached != null && cached.lastModified() == lastModified) {
                return cached;
            }

            String content = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (content.isEmpty()) {
                CachedDocumentEmbeddings emptyDocument = new CachedDocumentEmbeddings(path.getFileName().toString(), lastModified, List.of());
                documentCache.put(cacheKey, emptyDocument);
                return emptyDocument;
            }

            String normalized = content.replace("\r\n", "\n");
            List<String> chunks = splitIntoChunks(normalized);
            List<ChunkEmbedding> chunkEmbeddings = new ArrayList<>();
            for (int index = 0; index < chunks.size(); index++) {
                String chunk = chunks.get(index).trim();
                if (!chunk.isEmpty()) {
                    ChunkCandidate candidate = new ChunkCandidate(path.getFileName().toString(), index + 1, chunk);
                    chunkEmbeddings.add(new ChunkEmbedding(candidate, ollamaService.embed(chunk)));
                }
            }

            CachedDocumentEmbeddings rebuilt = new CachedDocumentEmbeddings(
                    path.getFileName().toString(),
                    lastModified,
                    chunkEmbeddings
            );
            documentCache.put(cacheKey, rebuilt);
            return rebuilt;
        } catch (IOException exception) {
            throw new RuntimeException("读取知识库文档失败: " + path.getFileName(), exception);
        }
    }

    private List<String> splitIntoChunks(String text) {
        int chunkSize = Math.max(300, vectorRagProperties.chunkSize());
        int overlap = Math.max(0, Math.min(vectorRagProperties.chunkOverlap(), chunkSize / 2));
        int step = Math.max(1, chunkSize - overlap);

        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < text.length(); start += step) {
            int end = Math.min(text.length(), start + chunkSize);
            chunks.add(text.substring(start, end));
            if (end >= text.length()) {
                break;
            }
        }
        return chunks;
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

    private String buildSystemPrompt() {
        return """
                你是一个项目知识库助手。
                请严格根据检索出的资料片段回答。
                如果资料中没有答案，请明确回答：资料中未找到相关信息。
                不要根据常识扩展，不要编造资料中没有出现的字段、接口、路径。
                回答末尾请单独列出“来源文件：”并写出使用到的文件名。
                """.trim();
    }

    private String buildPrompt(String question, List<ChunkMatch> matches) {
        StringBuilder builder = new StringBuilder();
        builder.append("【检索命中的知识库片段】\n");
        for (ChunkMatch match : matches) {
            builder.append("来源文件：").append(match.candidate().chunk().fileName())
                    .append(" | chunk#").append(match.candidate().chunk().chunkIndex())
                    .append(" | score=").append(roundScore(match.score()))
                    .append('\n');
            builder.append(match.candidate().chunk().content()).append("\n\n");
        }

        builder.append("【使用规则】\n")
                .append("1. 只能根据上面命中的资料片段回答。\n")
                .append("2. 如果资料不足，请回答：").append(NO_KNOWLEDGE_ANSWER).append('\n')
                .append("3. 不要补充片段中没有的信息。\n")
                .append("4. 回答最后列出来源文件。\n\n")
                .append("【用户问题】\n")
                .append(question);
        return builder.toString();
    }

    private double cosineSimilarity(List<Double> left, List<Double> right) {
        if (left.isEmpty() || right.isEmpty() || left.size() != right.size()) {
            return -1D;
        }

        double dot = 0D;
        double leftNorm = 0D;
        double rightNorm = 0D;
        for (int i = 0; i < left.size(); i++) {
            double a = left.get(i);
            double b = right.get(i);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }

        if (leftNorm == 0D || rightNorm == 0D) {
            return -1D;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private String sourceKey(ChunkMatch match) {
        return match.candidate().chunk().fileName() + "#" + match.candidate().chunk().chunkIndex();
    }

    private double roundScore(double value) {
        return Math.round(value * 10000D) / 10000D;
    }

    private String limitLength(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private record ChunkCandidate(String fileName, int chunkIndex, String content) {
    }

    private record ChunkEmbedding(ChunkCandidate chunk, List<Double> embedding) {
    }

    private record CachedDocumentEmbeddings(String fileName, long lastModified, List<ChunkEmbedding> chunks) {
    }

    private record ChunkMatch(ChunkEmbedding candidate, double score) {
    }
}
