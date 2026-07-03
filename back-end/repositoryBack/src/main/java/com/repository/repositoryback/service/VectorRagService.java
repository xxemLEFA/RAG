package com.repository.repositoryback.service;

import com.repository.repositoryback.config.KnowledgeProperties;
import com.repository.repositoryback.config.VectorRagProperties;
import com.repository.repositoryback.dto.AiRagResponse;
import com.repository.repositoryback.dto.KnowledgeFileItem;
import com.repository.repositoryback.dto.KnowledgeOverviewResponse;
import com.repository.repositoryback.dto.QdrantCollectionStatus;
import com.repository.repositoryback.dto.RagMatchItem;
import com.repository.repositoryback.dto.RagSourceItem;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Service
public class VectorRagService {

    private static final String NO_KNOWLEDGE_ANSWER = "资料中未找到相关信息。";
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final KnowledgeProperties knowledgeProperties;
    private final VectorRagProperties vectorRagProperties;
    private final OllamaService ollamaService;
    private final QdrantService qdrantService;
    private final Map<String, CachedDocumentEmbeddings> documentCache = new ConcurrentHashMap<>();
    private volatile String lastQdrantFingerprint = "";

    public VectorRagService(
            KnowledgeProperties knowledgeProperties,
            VectorRagProperties vectorRagProperties,
            OllamaService ollamaService,
            QdrantService qdrantService
    ) {
        this.knowledgeProperties = knowledgeProperties;
        this.vectorRagProperties = vectorRagProperties;
        this.ollamaService = ollamaService;
        this.qdrantService = qdrantService;
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

        if (qdrantService.isEnabled()) {
            return askWithQdrant(trimmedQuestion, candidates);
        }
        return askWithInMemory(trimmedQuestion, candidates);
    }

    public KnowledgeOverviewResponse inspectKnowledge() {
        return buildKnowledgeOverview(false);
    }

    public KnowledgeOverviewResponse rebuildKnowledgeIndex() {
        documentCache.clear();
        lastQdrantFingerprint = "";
        if (qdrantService.isEnabled()) {
            List<ChunkEmbedding> candidates = loadChunkEmbeddings();
            if (!candidates.isEmpty()) {
                qdrantService.recreateIndex(toQdrantChunks(candidates));
                lastQdrantFingerprint = buildChunkFingerprint(candidates);
            }
        }
        return buildKnowledgeOverview(false);
    }

    private AiRagResponse askWithQdrant(String question, List<ChunkEmbedding> candidates) {
        ensureQdrantIndex(candidates, false);

        List<Double> questionVector = ollamaService.embed(question);
        List<SearchHit> matches = qdrantService.query(questionVector, Math.max(1, vectorRagProperties.topK())).stream()
                .map(match -> new SearchHit(match.fileName(), match.chunkIndex(), match.content(), match.score()))
                .toList();

        if (matches.isEmpty()) {
            throw new RuntimeException("Qdrant 中没有返回可用的知识库命中片段");
        }
        return buildRagResponse(question, matches);
    }

    private AiRagResponse askWithInMemory(String question, List<ChunkEmbedding> candidates) {
        List<Double> questionVector = ollamaService.embed(question);
        List<SearchHit> matches = candidates.stream()
                .map(candidate -> new ChunkMatch(candidate, cosineSimilarity(questionVector, candidate.embedding())))
                .sorted(Comparator.comparingDouble(ChunkMatch::score).reversed())
                .limit(Math.max(1, vectorRagProperties.topK()))
                .map(match -> new SearchHit(
                        match.candidate().chunk().fileName(),
                        match.candidate().chunk().chunkIndex(),
                        match.candidate().chunk().content(),
                        match.score()
                ))
                .toList();
        return buildRagResponse(question, matches);
    }

    private AiRagResponse buildRagResponse(String question, List<SearchHit> matches) {
        String prompt = buildPrompt(question, matches);
        String answer = ollamaService.chatWithSystemPrompt(prompt, buildSystemPrompt());

        Map<String, RagSourceItem> sourceMap = new LinkedHashMap<>();
        for (SearchHit match : matches) {
            sourceMap.putIfAbsent(
                    sourceKey(match),
                    new RagSourceItem(
                            match.fileName(),
                            limitLength(match.content().replace('\n', ' '), 320),
                            roundScore(match.score())
                    )
            );
        }
        List<RagMatchItem> matchItems = matches.stream()
                .map(match -> new RagMatchItem(
                        match.fileName(),
                        match.chunkIndex(),
                        match.content(),
                        roundScore(match.score())
                ))
                .toList();

        boolean knowledgeHit = !answer.contains(NO_KNOWLEDGE_ANSWER);
        return new AiRagResponse(answer, new ArrayList<>(sourceMap.values()), knowledgeHit, matchItems);
    }

    private KnowledgeOverviewResponse buildKnowledgeOverview(boolean forceRebuild) {
        Path basePath = getKnowledgeBasePath();
        List<Path> matchedFiles = listKnowledgeFiles();
        int simpleLimit = Math.max(1, knowledgeProperties.maxFiles());
        int vectorLimit = Math.max(1, vectorRagProperties.maxFiles());
        int vectorChunkLimit = Math.max(1, vectorRagProperties.maxChunks());

        List<KnowledgeFileItem> files = new ArrayList<>();
        int totalVectorChunks = 0;
        for (int index = 0; index < matchedFiles.size(); index++) {
            Path path = matchedFiles.get(index);
            boolean usedByVectorRag = index < vectorLimit;
            int chunkCount = 0;
            if (usedByVectorRag) {
                CachedDocumentEmbeddings embeddings = forceRebuild
                        ? rebuildDocumentEmbeddings(path)
                        : loadOrBuildDocumentEmbeddings(path);
                chunkCount = embeddings.chunks().size();
                totalVectorChunks += chunkCount;
            }

            try {
                files.add(new KnowledgeFileItem(
                        toRelativePath(basePath, path),
                        Files.size(path),
                        TIME_FORMATTER.format(Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis())),
                        index < simpleLimit,
                        usedByVectorRag,
                        chunkCount
                ));
            } catch (IOException exception) {
                throw new RuntimeException("读取知识库文件信息失败: " + path.getFileName(), exception);
            }
        }

        int effectiveVectorChunks = totalVectorChunks;
        boolean vectorChunkLimitReached = totalVectorChunks > vectorChunkLimit;
        QdrantCollectionStatus qdrantStatus = qdrantService.inspectCollectionStatus();

        return new KnowledgeOverviewResponse(
                knowledgeProperties.baseDir(),
                knowledgeProperties.filePattern(),
                qdrantService.isEnabled() ? "qdrant" : "memory",
                simpleLimit,
                vectorLimit,
                vectorChunkLimit,
                matchedFiles.size(),
                totalVectorChunks,
                effectiveVectorChunks,
                vectorChunkLimitReached,
                qdrantStatus,
                files
        );
    }

    private List<ChunkEmbedding> loadChunkEmbeddings() {
        return listKnowledgeFiles().stream()
                .limit(Math.max(1, vectorRagProperties.maxFiles()))
                .map(this::loadOrBuildDocumentEmbeddings)
                .flatMap(cached -> cached.chunks().stream())
                .toList();
    }

    private CachedDocumentEmbeddings loadOrBuildDocumentEmbeddings(Path path) {
        return buildDocumentEmbeddings(path, false);
    }

    private CachedDocumentEmbeddings rebuildDocumentEmbeddings(Path path) {
        return buildDocumentEmbeddings(path, true);
    }

    private CachedDocumentEmbeddings buildDocumentEmbeddings(Path path, boolean forceRebuild) {
        try {
            long lastModified = Files.getLastModifiedTime(path).toMillis();
            String cacheKey = path.toAbsolutePath().normalize().toString();
            String relativePath = toRelativePath(getKnowledgeBasePath(), path);
            CachedDocumentEmbeddings cached = documentCache.get(cacheKey);
            if (!forceRebuild && cached != null && cached.lastModified() == lastModified) {
                return cached;
            }

            String content = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (content.isEmpty()) {
                CachedDocumentEmbeddings emptyDocument = new CachedDocumentEmbeddings(relativePath, lastModified, List.of());
                documentCache.put(cacheKey, emptyDocument);
                return emptyDocument;
            }

            String normalized = content.replace("\r\n", "\n");
            List<String> chunks = splitIntoChunks(normalized);
            List<ChunkEmbedding> chunkEmbeddings = new ArrayList<>();
            for (int index = 0; index < chunks.size(); index++) {
                String chunk = chunks.get(index).trim();
                if (!chunk.isEmpty()) {
                    ChunkCandidate candidate = new ChunkCandidate(relativePath, index + 1, chunk);
                    chunkEmbeddings.add(new ChunkEmbedding(candidate, ollamaService.embed(chunk)));
                }
            }

            CachedDocumentEmbeddings rebuilt = new CachedDocumentEmbeddings(
                    relativePath,
                    lastModified,
                    chunkEmbeddings
            );
            documentCache.put(cacheKey, rebuilt);
            return rebuilt;
        } catch (IOException exception) {
            throw new RuntimeException("读取知识库文档失败: " + path.getFileName(), exception);
        }
    }

    private List<Path> listKnowledgeFiles() {
        Path basePath = getKnowledgeBasePath();
        if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
            throw new RuntimeException("知识库目录不存在: " + basePath);
        }

        try (Stream<Path> pathStream = Files.walk(basePath)) {
            return pathStream
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesPattern(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> toRelativePath(basePath, path), String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException exception) {
            throw new RuntimeException("读取知识库文档失败: " + exception.getMessage(), exception);
        }
    }

    private Path getKnowledgeBasePath() {
        return Path.of(knowledgeProperties.baseDir());
    }

    private String toRelativePath(Path basePath, Path path) {
        return basePath.relativize(path).toString().replace('\\', '/');
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

    private String buildPrompt(String question, List<SearchHit> matches) {
        StringBuilder builder = new StringBuilder();
        builder.append("【检索命中的知识库片段】\n");
        for (SearchHit match : matches) {
            builder.append("来源文件：").append(match.fileName())
                    .append(" | chunk#").append(match.chunkIndex())
                    .append(" | score=").append(roundScore(match.score()))
                    .append('\n');
            builder.append(match.content()).append("\n\n");
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

    private String sourceKey(SearchHit match) {
        return match.fileName() + "#" + match.chunkIndex();
    }

    private void ensureQdrantIndex(List<ChunkEmbedding> candidates, boolean forceRebuild) {
        String currentFingerprint = buildChunkFingerprint(candidates);
        if (!forceRebuild && currentFingerprint.equals(lastQdrantFingerprint)) {
            return;
        }

        if (forceRebuild) {
            qdrantService.recreateIndex(toQdrantChunks(candidates));
        } else {
            qdrantService.ensureIndexed(toQdrantChunks(candidates));
        }
        lastQdrantFingerprint = currentFingerprint;
    }

    private List<QdrantService.IndexedChunk> toQdrantChunks(List<ChunkEmbedding> candidates) {
        return candidates.stream()
                .map(candidate -> new QdrantService.IndexedChunk(
                        candidate.chunk().fileName(),
                        candidate.chunk().chunkIndex(),
                        candidate.chunk().content(),
                        candidate.embedding()
                ))
                .toList();
    }

    private String buildChunkFingerprint(List<ChunkEmbedding> candidates) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (ChunkEmbedding candidate : candidates) {
                digest.update(candidate.chunk().fileName().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '#');
                digest.update(Integer.toString(candidate.chunk().chunkIndex()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '#');
                digest.update(candidate.chunk().content().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new RuntimeException("构建 Qdrant 索引指纹失败", exception);
        }
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

    private record SearchHit(String fileName, int chunkIndex, String content, double score) {
    }
}
