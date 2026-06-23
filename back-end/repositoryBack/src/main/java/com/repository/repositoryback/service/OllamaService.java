package com.repository.repositoryback.service;

import com.repository.repositoryback.config.OllamaProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class OllamaService {

    private final RestClient restClient;
    private final OllamaProperties ollamaProperties;

    public OllamaService(RestClient restClient, OllamaProperties ollamaProperties) {
        this.restClient = restClient;
        this.ollamaProperties = ollamaProperties;
    }

    public String chat(String question) {
        return chatWithSystemPrompt(question, ollamaProperties.systemPrompt());
    }

    public List<Double> embed(String input) {
        String trimmedInput = input == null ? "" : input.trim();
        if (trimmedInput.isEmpty()) {
            throw new IllegalArgumentException("input 不能为空");
        }

        String embeddingModel = ollamaProperties.embeddingModel();
        if (embeddingModel == null || embeddingModel.isBlank()) {
            throw new IllegalStateException("ollama.embedding-model 未配置，请重启后端并确认 application.properties 已生效");
        }

        Map<String, Object> requestBody = Map.of(
                "model", embeddingModel,
                "input", trimmedInput
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = restClient.post()
                    .uri(ollamaProperties.baseUrl() + "/api/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (responseBody == null) {
                throw new RuntimeException("Ollama embedding 响应为空");
            }

            Object embeddingsObject = responseBody.get("embeddings");
            if (!(embeddingsObject instanceof List<?> embeddings) || embeddings.isEmpty()) {
                throw new RuntimeException("Ollama embedding 返回中缺少 embeddings");
            }

            Object vectorObject = embeddings.get(0);
            if (!(vectorObject instanceof List<?> vector) || vector.isEmpty()) {
                throw new RuntimeException("Ollama embedding 返回中缺少向量内容");
            }

            return vector.stream()
                    .map(value -> {
                        if (value instanceof Number number) {
                            return number.doubleValue();
                        }
                        throw new RuntimeException("embedding 向量中存在非数值内容");
                    })
                    .toList();
        } catch (RestClientException exception) {
            throw new RuntimeException("调用 Ollama embedding 出错，请确认本地服务可访问: " + exception.getMessage(), exception);
        }
    }

    public String chatWithSystemPrompt(String question, String systemPrompt) {
        String trimmedQuestion = question == null ? "" : question.trim();
        if (trimmedQuestion.isEmpty()) {
            throw new IllegalArgumentException("question 不能为空");
        }

        String chatModel = ollamaProperties.chatModel();
        if (chatModel == null || chatModel.isBlank()) {
            throw new IllegalStateException("ollama.chat-model 未配置，请重启后端并确认 application.properties 已生效");
        }

        String resolvedSystemPrompt = systemPrompt == null || systemPrompt.isBlank()
                ? "你是一个项目开发助手，请用简洁、准确的中文回答。"
                : systemPrompt;

        Map<String, Object> requestBody = Map.of(
                "model", chatModel,
                "stream", false,
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", resolvedSystemPrompt
                        ),
                        Map.of(
                                "role", "user",
                                "content", trimmedQuestion
                        )
                )
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = restClient.post()
                    .uri(ollamaProperties.baseUrl() + "/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (responseBody == null) {
                throw new RuntimeException("Ollama 响应为空");
            }

            Object messageObject = responseBody.get("message");
            if (!(messageObject instanceof Map<?, ?> messageMap)) {
                throw new RuntimeException("Ollama 返回中缺少 message");
            }

            Object contentObject = messageMap.get("content");
            String content = Objects.toString(contentObject, "").trim();
            if (content.isEmpty()) {
                throw new RuntimeException("Ollama 返回中缺少 message.content");
            }

            return content;
        } catch (RestClientException exception) {
            throw new RuntimeException("调用 Ollama 出错，请确认本地服务可访问: " + exception.getMessage(), exception);
        }
    }
}
