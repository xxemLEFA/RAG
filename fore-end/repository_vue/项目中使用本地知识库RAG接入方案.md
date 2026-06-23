# 项目中使用本地知识库（RAG）接入方案

> 适用场景：在自己的 Spring Boot / Vue 项目中使用本地知识库问答能力。  
> 当前推荐本地模型组合：  
> - 语言模型：`deepseek-r1:7b` / `qwen2.5:7b` / `qwen2.5:14b`
> - 向量模型：`bge-m3:latest`
> - 模型服务：Ollama
> - 调试工具：Open WebUI
> - 正式项目接入：后端直接调用 Ollama API，并自行实现知识库检索流程

---

## 1. 什么是知识库问答

普通聊天是：

```text
用户问题
  ↓
语言模型
  ↓
模型回答
```

知识库问答是：

```text
用户问题
  ↓
检索知识库
  ↓
找到相关文档片段
  ↓
把“问题 + 文档片段”交给语言模型
  ↓
模型基于资料回答
```

这种方式一般叫做 **RAG**。

RAG 的完整含义是：

```text
Retrieval-Augmented Generation
检索增强生成
```

核心思想是：

> 先检索资料，再让模型基于资料回答。

---

## 2. 当前本地方案的角色分工

### 2.1 Ollama

Ollama 负责在本机运行模型。

默认地址：

```text
http://127.0.0.1:11434
```

如果后端运行在 Docker 容器里，访问宿主机 Ollama 时通常使用：

```text
http://host.docker.internal:11434
```

---

### 2.2 语言模型

语言模型负责生成最终回答。

例如：

```text
deepseek-r1:7b
qwen2.5:7b
qwen2.5:14b
```

语言模型适合：

- 根据知识库资料回答问题
- 总结文档
- 比较多个文档片段
- 生成解释说明
- 辅助分析项目问题

---

### 2.3 向量模型

向量模型负责把文本转换成向量。

当前推荐：

```text
bge-m3:latest
```

向量模型适合：

- 文档向量化
- 问题向量化
- 语义相似度检索
- 知识库检索

注意：

> `bge-m3` 不是聊天模型，不能直接用于自然语言对话。

---

### 2.4 Open WebUI

Open WebUI 适合作为：

- 模型测试台
- 知识库实验台
- RAG 效果验证工具
- 日志观察工具

不建议把 Open WebUI 直接作为正式业务系统的一部分。

正式项目更推荐：

```text
你的项目后端 → Ollama API
```

---

## 3. 推荐整体架构

```text
Vue 前端
  ↓
Spring Boot 后端
  ↓
知识库检索服务
  ↓
向量数据库 / 文档片段库
  ↓
Ollama bge-m3 生成向量
  ↓
Ollama 语言模型生成回答
  ↓
返回答案 + 来源
```

更清晰地拆开：

```text
文档入库阶段：
文档 → 解析 → 切块 → bge-m3 向量化 → 保存向量

用户提问阶段：
问题 → bge-m3 向量化 → 相似度检索 → 拼 Prompt → 语言模型回答
```

---

## 4. 项目中使用知识库的三种阶段

建议不要一开始就做完整知识库系统，而是分阶段实现。

---

## 第一阶段：固定文档 RAG

这是最简单、最适合入门的方式。

### 4.1 思路

先不使用向量库。

后端直接读取几个固定的 `.md` 文件，把文件内容拼进 Prompt，然后交给语言模型回答。

流程：

```text
用户问题
  ↓
读取固定 md 文档
  ↓
拼接 Prompt
  ↓
调用 Ollama /api/chat
  ↓
返回回答
```

### 4.2 适合场景

- 先验证知识库问答效果
- 文档数量很少
- 不想马上引入向量数据库
- 想先把项目链路跑通

### 4.3 文件目录示例

```text
src/main/resources/knowledge/
  ├─ plan-kind.md
  ├─ device-bind.md
  └─ treatment-preview.md
```

### 4.4 Prompt 示例

```text
你是一个项目知识库助手。

请严格根据【知识库资料】回答用户问题。

规则：
1. 只能使用资料中的内容。
2. 如果资料中没有答案，请回答：资料中未找到相关信息。
3. 不要根据常识扩展。
4. 不要编造资料中没有出现的字段、接口、路径。
5. 回答最后列出使用到的来源文件。

【知识库资料】
来源文件：plan-kind.md
内容：
...

来源文件：device-bind.md
内容：
...

【用户问题】
PLAN_KIND 字段是做什么的？
```

---

## 第二阶段：简单向量知识库

这一阶段开始使用 `bge-m3`。

### 5.1 文档入库流程

```text
上传文档
  ↓
读取文本
  ↓
切成多个 chunk
  ↓
调用 Ollama /api/embed
  ↓
得到向量
  ↓
保存 chunk 文本、文件名、向量
```

### 5.2 用户提问流程

```text
用户问题
  ↓
调用 Ollama /api/embed 生成问题向量
  ↓
用问题向量检索相似 chunk
  ↓
取 topK 相关片段
  ↓
拼接 Prompt
  ↓
调用 Ollama /api/chat
  ↓
返回答案和来源
```

---

## 第三阶段：完整知识库系统

完整知识库系统需要包含：

- 文件上传
- 文件列表
- 文件删除
- 重新索引
- 文档切块
- 向量生成
- 向量检索
- 知识库分类
- 问答记录
- 来源引用
- 权限控制
- 反馈机制

适合正式业务系统。

---

## 5. Ollama 相关接口

### 5.1 聊天接口：`/api/chat`

地址：

```text
POST http://127.0.0.1:11434/api/chat
```

作用：用于调用语言模型生成回答。

请求示例：

```json
{
  "model": "deepseek-r1:7b",
  "stream": false,
  "messages": [
    {
      "role": "system",
      "content": "你是一个项目知识库助手，请严格根据资料回答。"
    },
    {
      "role": "user",
      "content": "PLAN_KIND 字段是做什么的？"
    }
  ]
}
```

返回中重点字段：

```text
message.content
```

---

### 5.2 向量接口：`/api/embed`

地址：

```text
POST http://127.0.0.1:11434/api/embed
```

作用：用于生成文本向量。

请求示例：

```json
{
  "model": "bge-m3:latest",
  "input": "PLAN_KIND 字段用于区分方案类型。"
}
```

返回示例：

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

---

### 5.3 查看本地模型：`/api/tags`

地址：

```text
GET http://127.0.0.1:11434/api/tags
```

作用：查看本机已安装模型。

---

## 6. 后端接口设计建议

### 6.1 普通聊天接口

```text
POST /api/ai/chat
```

请求：

```json
{
  "question": "帮我解释一下什么是 RAG"
}
```

返回：

```json
{
  "answer": "RAG 是检索增强生成，先检索资料，再让模型基于资料回答。"
}
```

---

### 6.2 简单知识库问答接口

```text
POST /api/ai/rag-simple
```

请求：

```json
{
  "question": "PLAN_KIND 字段是做什么的？"
}
```

内部逻辑：

```text
读取 resources/knowledge 下的 md 文件
  ↓
拼接 Prompt
  ↓
调用 Ollama /api/chat
  ↓
返回回答
```

---

### 6.3 向量知识库问答接口

```text
POST /api/ai/rag
```

请求：

```json
{
  "question": "PLAN_KIND 字段是做什么的？",
  "knowledgeBaseId": "project-docs"
}
```

返回：

```json
{
  "answer": "PLAN_KIND 用于区分方案类型，例如 ZL 治疗方案、FC 复查方案、SS 膳食方案、QT 其他方案。",
  "sources": [
    {
      "fileName": "plan-kind.md",
      "chunkText": "方案类型：ZL治疗方案，FC复查方案，SS膳食方案，QT其他方案"
    }
  ]
}
```

---

### 6.4 文档上传接口

```text
POST /api/knowledge/upload
```

作用：

- 上传文档
- 解析文本
- 切块
- 生成向量
- 保存到向量库

---

### 6.5 文档重新索引接口

```text
POST /api/knowledge/reindex/{fileId}
```

作用：

- 删除旧 chunk
- 重新读取文档
- 重新切块
- 重新生成向量
- 保存新索引

---

## 7. 数据表设计建议

如果先做轻量版本，可以用普通数据库保存文档和片段。

### 7.1 知识库表

```sql
CREATE TABLE AI_KNOWLEDGE_BASE (
    ID VARCHAR2(64) PRIMARY KEY,
    NAME VARCHAR2(200),
    DESCRIPTION VARCHAR2(1000),
    CREATE_TIME DATE,
    UPDATE_TIME DATE
);
```

---

### 7.2 知识库文件表

```sql
CREATE TABLE AI_KNOWLEDGE_FILE (
    ID VARCHAR2(64) PRIMARY KEY,
    KB_ID VARCHAR2(64),
    FILE_NAME VARCHAR2(500),
    FILE_PATH VARCHAR2(1000),
    FILE_TYPE VARCHAR2(50),
    STATUS VARCHAR2(50),
    CREATE_TIME DATE,
    UPDATE_TIME DATE
);
```

---

### 7.3 文档片段表

```sql
CREATE TABLE AI_KNOWLEDGE_CHUNK (
    ID VARCHAR2(64) PRIMARY KEY,
    KB_ID VARCHAR2(64),
    FILE_ID VARCHAR2(64),
    CHUNK_INDEX NUMBER,
    CHUNK_TEXT CLOB,
    CREATE_TIME DATE
);
```

---

### 7.4 向量存储说明

如果使用 Oracle 普通表，不建议直接做大规模向量检索。

正式方案建议使用：

- Qdrant
- Milvus
- PostgreSQL + pgvector
- Elasticsearch / OpenSearch 向量检索

如果只是实验阶段，可以先不做向量库，先做固定文档 RAG。

---

## 8. 文档切块建议

文档切块会直接影响知识库效果。

### 8.1 推荐切块规则

优先按 Markdown 标题切块：

```text
# 一级标题
## 二级标题
### 三级标题
```

如果没有标题，再按长度切块。

建议初始参数：

```text
chunkSize = 800 ~ 1200 字
chunkOverlap = 100 ~ 200 字
```

---

### 8.2 每个 chunk 建议保存的信息

```json
{
  "chunkId": "xxx",
  "fileName": "plan-kind.md",
  "chunkIndex": 1,
  "chunkText": "文本内容",
  "metadata": {
    "disease": "高血压",
    "module": "PLAN_KIND",
    "type": "字段说明"
  }
}
```

---

## 9. Prompt 模板

### 9.1 严格知识库问答 Prompt

```text
你是一个项目知识库助手。

请严格根据【知识库资料】回答用户问题。

规则：
1. 只能使用知识库资料中的内容。
2. 如果资料中没有答案，请回答：资料中未找到相关信息。
3. 不要根据常识扩展。
4. 不要编造资料中没有出现的字段、接口、路径。
5. 不要把不同来源的内容混在一起。
6. 回答最后列出使用到的来源文件名。

【知识库资料】
{{context}}

【用户问题】
{{question}}
```

---

### 9.2 文件对比 Prompt

```text
你是一个项目文档对比助手。

请只比较下面提供的两个文件内容。
不要引用其他文件。
不要根据常识补充。

【文件A】
文件名：{{fileAName}}
内容：
{{fileAContent}}

【文件B】
文件名：{{fileBName}}
内容：
{{fileBContent}}

【用户问题】
{{question}}
```

---

### 9.3 文件名抽取 Prompt

```text
请只根据检索结果中的文件名回答。

规则：
1. 只输出文件名。
2. 不要解释。
3. 不要总结。
4. 不要输出正文内容。
5. 如果没有匹配文件，请回答：未找到匹配文件。

【检索结果】
{{sources}}

【用户问题】
{{question}}
```

---

## 10. Spring Boot 固定文档 RAG 示例思路

### 10.1 读取 resources 下 md 文件

```java
ClassPathResource resource = new ClassPathResource("knowledge/plan-kind.md");
String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
```

---

### 10.2 拼接 Prompt

```java
String prompt = "你是一个项目知识库助手。\n"
        + "请严格根据【知识库资料】回答用户问题。\n"
        + "如果资料中没有答案，请回答：资料中未找到相关信息。\n\n"
        + "【知识库资料】\n"
        + content
        + "\n\n【用户问题】\n"
        + question;
```

---

### 10.3 调用 `/api/chat`

```json
{
  "model": "deepseek-r1:7b",
  "stream": false,
  "messages": [
    {
      "role": "user",
      "content": "拼接后的 prompt"
    }
  ]
}
```

---

## 11. Spring Boot 向量 RAG 核心伪代码

```java
public RagAnswer rag(String question, String knowledgeBaseId) {
    // 1. 调用 bge-m3 生成问题向量
    List<Double> questionVector = ollamaEmbeddingService.embed(question);

    // 2. 去向量库检索相似 chunk
    List<KnowledgeChunk> chunks = vectorStore.search(knowledgeBaseId, questionVector, 5);

    // 3. 拼接上下文
    String context = buildContext(chunks);

    // 4. 构造严格 Prompt
    String prompt = buildRagPrompt(context, question);

    // 5. 调用语言模型
    String answer = ollamaChatService.chat(prompt);

    // 6. 返回答案和来源
    return new RagAnswer(answer, chunks);
}
```

---

## 12. 前端展示建议

前端页面至少展示：

- 用户问题
- AI 回答
- 引用来源
- 来源文件名
- 来源片段
- 是否命中知识库

示例结构：

```text
问题：
PLAN_KIND 字段是做什么的？

回答：
PLAN_KIND 用于区分方案类型，例如 ZL、FC、SS、QT。

引用来源：
1. plan-kind.md
   方案类型：ZL治疗方案，FC复查方案，SS膳食方案，QT其他方案
```

---

## 13. 日志排查方式

如果使用 Open WebUI 测试知识库，可以看日志里的：

```text
embedding_config
```

如果看到：

```text
{'engine': 'ollama', 'model': 'bge-m3:latest'}
```

说明当前文档是用 `bge-m3` 做的向量。

如果看到：

```text
{'engine': '', 'model': 'sentence-transformers/all-MiniLM-L6-v2'}
```

说明还是旧的默认向量模型。

---

## 14. 当前推荐落地路线

### 第一步

完成普通聊天：

```text
/api/ai/chat
```

---

### 第二步

完成固定文档知识库问答：

```text
/api/ai/rag-simple
```

---

### 第三步

引入 `bge-m3` 向量化：

```text
/api/embed
```

---

### 第四步

引入向量数据库：

```text
Qdrant / pgvector / Milvus
```

---

### 第五步

完成正式知识库：

```text
文件上传
文档切块
向量索引
相似检索
引用来源
重新索引
```

---

## 15. 当前建议

现在最建议先做：

```text
固定文档 RAG
```

不要一开始就做完整知识库系统。

原因：

- 开发量小
- 容易调试
- 能快速验证模型效果
- 能先把业务页面跑起来
- 后面可以平滑升级到向量知识库

一句话总结：

> 先把“读取固定 md + 拼 Prompt + 调 Ollama”跑通，再做真正的向量知识库。
