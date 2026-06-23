# 本地项目接入 Ollama 接口说明

> 适用场景：Spring Boot / Vue 项目中接入本地大模型能力。  
> 当前本地方案建议：  
> - 语言模型：`deepseek-r1:7b` 或后续替换为更强模型  
> - 向量模型：`bge-m3:latest`  
> - 模型服务：Ollama  
> - 调试界面：Open WebUI  
> - 项目正式接入：后端直接调用 Ollama API  

---

## 1. Ollama 在当前方案中的角色

Ollama 是本地模型运行服务，负责在本机运行语言模型和向量模型。

在你的项目中可以把 Ollama 理解为一个本地 HTTP 服务：

```text
http://127.0.0.1:11434
```

项目后端通过 HTTP 请求调用 Ollama，然后把模型返回结果再返回给前端。

推荐架构：

```text
前端 Vue
   ↓
Spring Boot 后端
   ↓
Ollama API
   ↓
本地语言模型 / 向量模型
```

Open WebUI 主要作为调试台和知识库实验台，不建议直接作为正式项目的一部分。

---

## 2. 当前本地模型分工

### 2.1 语言模型

语言模型负责生成回答。

当前可以使用：

```text
deepseek-r1:7b
```

也可以后续替换成：

```text
qwen2.5:7b
qwen2.5:14b
deepseek-r1:14b
```

语言模型适合处理：

- 对话问答
- 文档总结
- 代码解释
- SQL 分析
- 项目问题答疑
- 根据检索结果生成最终回答

---

### 2.2 向量模型

向量模型负责把文本转换成向量，用于语义检索。

当前建议使用：

```text
bge-m3:latest
```

向量模型适合处理：

- 文档向量化
- 用户问题向量化
- 语义相似度检索
- RAG 知识库检索

注意：`bge-m3` 不是聊天模型，不能直接拿来做自然语言对话。

---

## 3. 常用 Ollama 命令

### 3.1 查看本地已安装模型

```powershell
ollama list
```

示例输出可能包含：

```text
deepseek-r1:7b
bge-m3:latest
llama3.2:1b
```

---

### 3.2 下载语言模型

```powershell
ollama pull deepseek-r1:7b
```

或者：

```powershell
ollama pull qwen2.5:7b
```

---

### 3.3 下载向量模型

```powershell
ollama pull bge-m3
```

---

### 3.4 运行模型测试

```powershell
ollama run deepseek-r1:7b
```

进入交互后可以输入：

```text
你好，简单介绍一下你自己
```

退出：

```text
/bye
```

---

## 4. Ollama API 基础信息

### 4.1 默认服务地址

```text
http://127.0.0.1:11434
```

如果是 Docker 容器访问宿主机上的 Ollama，通常使用：

```text
http://host.docker.internal:11434
```

你的 Open WebUI 就是通过这个地址访问宿主机 Ollama。

---

### 4.2 API 常用接口概览

| 接口 | 方法 | 作用 |
|---|---|---|
| `/api/chat` | POST | 聊天对话，推荐项目接入使用 |
| `/api/generate` | POST | 单轮文本生成 |
| `/api/embed` | POST | 生成文本向量 |
| `/api/tags` | GET | 查看本地模型列表 |
| `/api/show` | POST | 查看模型详细信息 |
| `/api/pull` | POST | 拉取模型 |
| `/api/ps` | GET | 查看正在运行的模型 |
| `/api/version` | GET | 查看 Ollama 版本 |

---

## 5. 聊天接口：`/api/chat`

### 5.1 接口地址

```text
POST http://127.0.0.1:11434/api/chat
```

### 5.2 作用

用于多轮聊天。项目后端接入时，优先推荐使用这个接口。

### 5.3 请求示例

```json
{
  "model": "deepseek-r1:7b",
  "stream": false,
  "messages": [
    {
      "role": "system",
      "content": "你是一个项目开发助手，请用简洁、准确的中文回答。"
    },
    {
      "role": "user",
      "content": "帮我解释一下 Spring Boot 如何调用 Ollama。"
    }
  ]
}
```

### 5.4 curl 示例

```powershell
curl http://127.0.0.1:11434/api/chat -d '{
  "model": "deepseek-r1:7b",
  "stream": false,
  "messages": [
    {
      "role": "user",
      "content": "你好，请简单介绍一下你自己"
    }
  ]
}'
```

### 5.5 返回示例

```json
{
  "model": "deepseek-r1:7b",
  "created_at": "2026-04-26T13:41:12.000Z",
  "message": {
    "role": "assistant",
    "content": "你好，我是一个本地运行的 AI 助手。"
  },
  "done": true
}
```

项目中主要读取：

```text
message.content
```

---

## 6. 文本生成接口：`/api/generate`

### 6.1 接口地址

```text
POST http://127.0.0.1:11434/api/generate
```

### 6.2 作用

用于单轮文本生成。相比 `/api/chat`，它更简单，但不太适合多轮对话。

### 6.3 请求示例

```json
{
  "model": "deepseek-r1:7b",
  "prompt": "用一句话解释什么是 RAG。",
  "stream": false
}
```

### 6.4 curl 示例

```powershell
curl http://127.0.0.1:11434/api/generate -d '{
  "model": "deepseek-r1:7b",
  "prompt": "用一句话解释什么是 RAG。",
  "stream": false
}'
```

### 6.5 返回中重点字段

```text
response
```

---

## 7. 向量接口：`/api/embed`

### 7.1 接口地址

```text
POST http://127.0.0.1:11434/api/embed
```

### 7.2 作用

用于生成文本向量，通常用于知识库、语义检索和 RAG。

### 7.3 请求示例

```json
{
  "model": "bge-m3:latest",
  "input": "糖尿病治疗方案预览页面说明"
}
```

### 7.4 curl 示例

```powershell
curl http://127.0.0.1:11434/api/embed -d '{
  "model": "bge-m3:latest",
  "input": "糖尿病治疗方案预览页面说明"
}'
```

### 7.5 返回示例结构

```json
{
  "model": "bge-m3:latest",
  "embeddings": [
    [
      0.0123,
      -0.0456,
      0.0789
    ]
  ]
}
```

注意：Ollama 只负责生成向量，不负责保存向量，也不负责检索。  
如果项目要自己做 RAG，需要额外准备向量数据库或向量存储方案。

常见选择：

- Chroma
- Milvus
- Qdrant
- PostgreSQL + pgvector
- Elasticsearch / OpenSearch 向量检索
- 自己用数据库保存向量并计算相似度，适合实验，不适合大规模正式使用

---

## 8. 模型列表接口：`/api/tags`

### 8.1 接口地址

```text
GET http://127.0.0.1:11434/api/tags
```

### 8.2 curl 示例

```powershell
curl http://127.0.0.1:11434/api/tags
```

### 8.3 作用

用于查看本地 Ollama 已经安装的模型。

项目启动时可以调用它来检查模型是否存在。

---

## 9. 查看模型详情：`/api/show`

### 9.1 接口地址

```text
POST http://127.0.0.1:11434/api/show
```

### 9.2 请求示例

```json
{
  "model": "deepseek-r1:7b"
}
```

### 9.3 curl 示例

```powershell
curl http://127.0.0.1:11434/api/show -d '{
  "model": "deepseek-r1:7b"
}'
```

### 9.4 作用

用于查看模型详细信息，例如模型参数、模板、Modelfile 信息等。

---

## 10. 查看正在运行的模型：`/api/ps`

### 10.1 接口地址

```text
GET http://127.0.0.1:11434/api/ps
```

### 10.2 curl 示例

```powershell
curl http://127.0.0.1:11434/api/ps
```

### 10.3 作用

用于查看当前正在内存中运行的模型。

---

## 11. 查看 Ollama 版本：`/api/version`

### 11.1 接口地址

```text
GET http://127.0.0.1:11434/api/version
```

### 11.2 curl 示例

```powershell
curl http://127.0.0.1:11434/api/version
```

---

## 12. Spring Boot 接入 Ollama 示例

### 12.1 Maven 依赖

如果项目已经有 Spring Web，一般不需要额外依赖。  
如果没有，需要引入：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

Jackson 一般由 Spring Boot Web 自动引入。

---

### 12.2 请求 DTO

```java
package com.example.ai.dto;

public class AiChatRequest {
    private String question;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
```

---

### 12.3 响应 DTO

```java
package com.example.ai.dto;

public class AiChatResponse {
    private String answer;

    public AiChatResponse() {
    }

    public AiChatResponse(String answer) {
        this.answer = answer;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }
}
```

---

### 12.4 OllamaService

```java
package com.example.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class OllamaService {

    private static final String OLLAMA_URL = "http://127.0.0.1:11434/api/chat";
    private static final String MODEL_NAME = "deepseek-r1:7b";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String chat(String question) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", MODEL_NAME);
            requestBody.put("stream", false);

            List<Map<String, String>> messages = new ArrayList<>();

            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", "你是一个项目开发助手，请用简洁、准确的中文回答。");
            messages.add(systemMsg);

            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", question);
            messages.add(userMsg);

            requestBody.put("messages", messages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    OLLAMA_URL,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("调用 Ollama 失败，响应为空或状态异常");
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode contentNode = root.path("message").path("content");

            if (contentNode.isMissingNode()) {
                throw new RuntimeException("Ollama 返回中未找到 message.content");
            }

            return contentNode.asText();

        } catch (Exception e) {
            throw new RuntimeException("调用 Ollama 出错：" + e.getMessage(), e);
        }
    }
}
```

---

### 12.5 Controller

```java
package com.example.ai.controller;

import com.example.ai.dto.AiChatRequest;
import com.example.ai.dto.AiChatResponse;
import com.example.ai.service.OllamaService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin
public class AiController {

    private final OllamaService ollamaService;

    public AiController(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
    }

    @PostMapping("/chat")
    public AiChatResponse chat(@RequestBody AiChatRequest request) {
        String answer = ollamaService.chat(request.getQuestion());
        return new AiChatResponse(answer);
    }
}
```

---

## 13. Vue + Axios 调用示例

### 13.1 API 方法

```javascript
import axios from "axios";

export function aiChat(question) {
  return axios.post("/api/ai/chat", {
    question: question
  });
}
```

---

### 13.2 页面中使用

```javascript
aiChat("请解释一下什么是 RAG").then(res => {
  console.log(res.data.answer);
});
```

---

## 14. RAG 知识库问答的基本流程

如果后续要在项目中实现知识库问答，不是只调用 `/api/chat`，而是需要增加检索步骤。

完整流程：

```text
用户问题
   ↓
调用 bge-m3 生成问题向量
   ↓
到向量库中检索相似文档片段
   ↓
拼接 prompt
   ↓
调用 deepseek-r1:7b 生成回答
   ↓
返回答案 + 引用来源
```

---

## 15. RAG Prompt 示例

后端可以把检索结果拼成如下 prompt：

```text
你是一个项目知识库助手。
请严格根据【资料内容】回答用户问题。
如果资料中没有答案，请回答：资料中未找到相关信息。
不要根据常识扩展，不要编造。

【资料内容】
文档1：2026-03-26-tnb-zhuanzhen-entry-points.md
内容：
...

文档2：2026-03-31-personhome-followup-hospital-lookup.md
内容：
...

【用户问题】
这些文档主要涉及哪些模块？
```

这样可以减少模型自由发挥。

---

## 16. 项目落地建议

### 第一阶段：只做普通聊天

先做：

```text
/api/ai/chat
```

目标：

- 前端能发问题
- 后端能调用 Ollama
- 页面能展示回答

---

### 第二阶段：做固定文档 RAG

先不要做完整知识库管理。  
可以先手动准备几份文档，后端读取后拼 prompt 测试。

目标：

- 验证模型是否能严格根据资料回答
- 验证 Prompt 是否稳定
- 验证前端展示方式

---

### 第三阶段：做真正知识库

后端增加：

- 文件上传
- 文档解析
- 文档切块
- 调用 `/api/embed`
- 存入向量库
- 相似度检索
- 生成回答
- 返回引用来源

---

## 17. 当前方案中的注意事项

### 17.1 Ollama 必须保持运行

如果 Ollama 没启动，项目会调用失败。

可以用下面命令检查：

```powershell
ollama list
```

或者：

```powershell
curl http://127.0.0.1:11434/api/tags
```

---

### 17.2 Docker 容器访问本机 Ollama

如果后端也跑在 Docker 容器里，不能直接用：

```text
http://127.0.0.1:11434
```

因为容器里的 `127.0.0.1` 是容器自己。

这时一般用：

```text
http://host.docker.internal:11434
```

---

### 17.3 不要把 Open WebUI 当正式后端

Open WebUI 适合：

- 测模型
- 测知识库
- 看日志
- 验证 RAG 效果

正式项目建议：

```text
项目后端直接调用 Ollama
```

---

### 17.4 语言模型和向量模型不要混用

| 模型 | 用途 |
|---|---|
| `deepseek-r1:7b` | 聊天、回答、总结 |
| `bge-m3:latest` | 文本向量化、语义检索 |

不要用 `bge-m3` 做聊天。

---

### 17.5 生成内容要做超时处理

大模型生成可能比较慢，后端建议设置：

- 请求超时时间
- 最大输出长度
- 错误提示
- 日志记录

---

## 18. 推荐配置项

建议把模型和地址放到配置文件里，不要写死在 Java 代码中。

### application.yml 示例

```yaml
ollama:
  base-url: http://127.0.0.1:11434
  chat-model: deepseek-r1:7b
  embedding-model: bge-m3:latest
```

后续如果换模型，只需要改配置，不需要改代码。

---

## 19. 官方参考

- Ollama Chat API：`POST /api/chat`，用于生成下一条聊天消息。
- Ollama Embedding API：`POST /api/embed`，用于生成文本向量。
- Ollama Embeddings 能把文本转换成数值向量，用于语义搜索、检索和 RAG。
- Ollama 支持 OpenAI 兼容接口，但正式项目中直接使用 Ollama 原生 API 会更清晰。

参考地址：

```text
https://docs.ollama.com/api/chat
https://docs.ollama.com/api/embed
https://docs.ollama.com/capabilities/embeddings
https://docs.ollama.com/api/openai-compatibility
```

---

## 20. 当前推荐路线总结

当前最推荐的项目接入路线：

```text
第一步：Spring Boot 调 Ollama /api/chat
第二步：Vue 页面接 /api/ai/chat
第三步：验证 deepseek-r1:7b 回答效果
第四步：后端接 /api/embed
第五步：接入向量库
第六步：实现 /api/ai/rag
```

一句话总结：

> Open WebUI 用来调试，Ollama API 用来正式接入项目。
