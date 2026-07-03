package com.repository.repositoryback.service;

import com.repository.repositoryback.config.QdrantProperties;
import com.repository.repositoryback.dto.QdrantCollectionStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class QdrantService {

    private final RestClient restClient;
    private final QdrantProperties qdrantProperties;

    public QdrantService(RestClient restClient, QdrantProperties qdrantProperties) {
        this.restClient = restClient;
        this.qdrantProperties = qdrantProperties;
    }

    public boolean isEnabled() {
        return qdrantProperties.enabled();
    }

    public QdrantCollectionStatus inspectCollectionStatus() {
        if (!isEnabled()) {
            return new QdrantCollectionStatus(false, qdrantProperties.baseUrl(), qdrantProperties.collectionName(), false, null, "Qdrant 未启用");
        }

        try {
            CollectionInfo collectionInfo = fetchCollectionInfo();
            if (collectionInfo == null) {
                return new QdrantCollectionStatus(true, qdrantProperties.baseUrl(), qdrantProperties.collectionName(), false, 0, "Qdrant collection 尚未创建");
            }

            int indexedPoints = countPoints();
            return new QdrantCollectionStatus(true, qdrantProperties.baseUrl(), qdrantProperties.collectionName(), true, indexedPoints, "Qdrant collection 可用");
        } catch (RuntimeException exception) {
            return new QdrantCollectionStatus(true, qdrantProperties.baseUrl(), qdrantProperties.collectionName(), false, null, exception.getMessage());
        }
    }

    public QdrantCollectionStatus recreateIndex(List<IndexedChunk> chunks) {
        ensureConfigured();
        deleteCollectionIfExists();
        return ensureIndexed(chunks);
    }

    public QdrantCollectionStatus ensureIndexed(List<IndexedChunk> chunks) {
        ensureConfigured();
        if (chunks.isEmpty()) {
            throw new RuntimeException("没有可写入 Qdrant 的知识库 chunk");
        }

        int vectorSize = chunks.get(0).embedding().size();
        CollectionInfo collectionInfo = fetchCollectionInfo();
        if (collectionInfo == null) {
            createCollection(vectorSize);
        } else if (collectionInfo.vectorSize() != null && collectionInfo.vectorSize() != vectorSize) {
            deleteCollectionIfExists();
            createCollection(vectorSize);
        }

        upsertPoints(chunks);
        return inspectCollectionStatus();
    }

    public List<QdrantMatch> query(List<Double> vector, int limit) {
        ensureConfigured();
        if (vector == null || vector.isEmpty()) {
            throw new IllegalArgumentException("query vector 不能为空");
        }

        Map<String, Object> requestBody = Map.of(
                "query", vector,
                "limit", Math.max(1, limit),
                "with_payload", true
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = request("POST", collectionPointsQueryUri(), requestBody);
            Object resultObject = responseBody.get("result");
            if (!(resultObject instanceof Map<?, ?> resultMap)) {
                throw new RuntimeException("Qdrant query 返回中缺少 result");
            }

            Object pointsObject = resultMap.get("points");
            if (!(pointsObject instanceof List<?> pointList)) {
                throw new RuntimeException("Qdrant query 返回中缺少 points");
            }

            List<QdrantMatch> matches = new ArrayList<>();
            for (Object pointObject : pointList) {
                if (!(pointObject instanceof Map<?, ?> pointMap)) {
                    continue;
                }

                Object payloadObject = pointMap.get("payload");
                if (!(payloadObject instanceof Map<?, ?> payloadMap)) {
                    continue;
                }

                String fileName = Objects.toString(payloadMap.get("fileName"), "").trim();
                String content = Objects.toString(payloadMap.get("content"), "").trim();
                int chunkIndex = toInt(payloadMap.get("chunkIndex"));
                double score = toDouble(pointMap.get("score"));
                if (!fileName.isEmpty() && !content.isEmpty() && chunkIndex > 0) {
                    matches.add(new QdrantMatch(fileName, chunkIndex, content, score));
                }
            }
            return matches;
        } catch (RestClientException exception) {
            throw new RuntimeException("调用 Qdrant query 失败: " + exception.getMessage(), exception);
        }
    }

    private void createCollection(int vectorSize) {
        Map<String, Object> requestBody = Map.of(
                "vectors", Map.of(
                        "size", vectorSize,
                        "distance", resolveDistance()
                )
        );

        try {
            request("PUT", collectionUri(), requestBody);
        } catch (RestClientException exception) {
            throw new RuntimeException("创建 Qdrant collection 失败: " + exception.getMessage(), exception);
        }
    }

    private void deleteCollectionIfExists() {
        try {
            request("DELETE", collectionUri(), null);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return;
            }
            throw new RuntimeException("删除 Qdrant collection 失败: " + exception.getMessage(), exception);
        } catch (RestClientException exception) {
            throw new RuntimeException("删除 Qdrant collection 失败: " + exception.getMessage(), exception);
        }
    }

    private CollectionInfo fetchCollectionInfo() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = request("GET", collectionUri(), null);
            Object resultObject = responseBody.get("result");
            if (!(resultObject instanceof Map<?, ?> resultMap)) {
                return new CollectionInfo(null);
            }

            Object configObject = resultMap.get("config");
            if (!(configObject instanceof Map<?, ?> configMap)) {
                return new CollectionInfo(null);
            }

            Object paramsObject = configMap.get("params");
            if (!(paramsObject instanceof Map<?, ?> paramsMap)) {
                return new CollectionInfo(null);
            }

            Object vectorsObject = paramsMap.get("vectors");
            if (vectorsObject instanceof Map<?, ?> vectorMap) {
                Object sizeObject = vectorMap.get("size");
                if (sizeObject instanceof Number number) {
                    return new CollectionInfo(number.intValue());
                }
            }
            return new CollectionInfo(null);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return null;
            }
            throw new RuntimeException("读取 Qdrant collection 状态失败: " + exception.getMessage(), exception);
        } catch (RestClientException exception) {
            throw new RuntimeException("读取 Qdrant collection 状态失败: " + exception.getMessage(), exception);
        }
    }

    private int countPoints() {
        Map<String, Object> requestBody = Map.of("exact", true);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = request("POST", collectionPointsCountUri(), requestBody);
            Object resultObject = responseBody.get("result");
            if (!(resultObject instanceof Map<?, ?> resultMap)) {
                throw new RuntimeException("Qdrant count 返回中缺少 result");
            }
            return toInt(resultMap.get("count"));
        } catch (RestClientException exception) {
            throw new RuntimeException("读取 Qdrant point 数量失败: " + exception.getMessage(), exception);
        }
    }

    private void upsertPoints(List<IndexedChunk> chunks) {
        int batchSize = Math.max(1, qdrantProperties.batchSize());
        for (int start = 0; start < chunks.size(); start += batchSize) {
            int end = Math.min(chunks.size(), start + batchSize);
            List<Map<String, Object>> points = new ArrayList<>();
            for (IndexedChunk chunk : chunks.subList(start, end)) {
                points.add(Map.of(
                        "id", pointId(chunk),
                        "vector", chunk.embedding(),
                        "payload", Map.of(
                                "fileName", chunk.fileName(),
                                "chunkIndex", chunk.chunkIndex(),
                                "content", chunk.content()
                        )
                ));
            }

            Map<String, Object> requestBody = Map.of("points", points);
            try {
                request("PUT", collectionPointsUri() + "?wait=true", requestBody);
            } catch (RestClientException exception) {
                throw new RuntimeException("写入 Qdrant points 失败: " + exception.getMessage(), exception);
            }
        }
    }

    private Map<String, Object> request(String method, String uri, Object body) {
        RestClient.RequestBodyUriSpec requestSpec = restClient.method(HttpMethod.valueOf(method));
        RestClient.RequestBodySpec requestBodySpec = requestSpec.uri(uri)
                .headers(headers -> {
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    String apiKey = qdrantProperties.apiKey();
                    if (apiKey != null && !apiKey.isBlank()) {
                        headers.set("api-key", apiKey);
                    }
                });

        RestClient.ResponseSpec responseSpec = body == null
                ? requestBodySpec.retrieve()
                : requestBodySpec.body(body).retrieve();

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = responseSpec.body(Map.class);
        if (responseBody == null) {
            throw new RuntimeException("Qdrant 响应为空");
        }
        return responseBody;
    }

    private void ensureConfigured() {
        if (!isEnabled()) {
            throw new IllegalStateException("Qdrant 未启用");
        }
        if (qdrantProperties.baseUrl() == null || qdrantProperties.baseUrl().isBlank()) {
            throw new IllegalStateException("qdrant.base-url 未配置");
        }
        if (qdrantProperties.collectionName() == null || qdrantProperties.collectionName().isBlank()) {
            throw new IllegalStateException("qdrant.collection-name 未配置");
        }
    }

    private String resolveDistance() {
        String distance = qdrantProperties.distance();
        return distance == null || distance.isBlank() ? "Cosine" : distance;
    }

    private String pointId(IndexedChunk chunk) {
        String source = chunk.fileName() + "#" + chunk.chunkIndex();
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0D;
    }

    private String collectionUri() {
        return qdrantProperties.baseUrl() + "/collections/" + qdrantProperties.collectionName();
    }

    private String collectionPointsUri() {
        return collectionUri() + "/points";
    }

    private String collectionPointsQueryUri() {
        return collectionUri() + "/points/query";
    }

    private String collectionPointsCountUri() {
        return collectionUri() + "/points/count";
    }

    public record IndexedChunk(String fileName, int chunkIndex, String content, List<Double> embedding) {
    }

    public record QdrantMatch(String fileName, int chunkIndex, String content, double score) {
    }

    private record CollectionInfo(Integer vectorSize) {
    }
}
