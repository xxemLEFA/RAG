# RGA

本项目是一个本地 AI / RAG 示例仓库，包含前端页面、Spring Boot 后端接口，以及供 RAG 使用的本地知识库文档。

## 项目结构

```text
P:\AIprogect\repository
├─ fore-end\
│  └─ repository_vue\        Vue 3 + TypeScript + Vite 前端
└─ back-end\
   └─ repositoryBack\        Spring Boot 后端
```

相关目录：

- 前端：`fore-end/repository_vue`
- 后端：`back-end/repositoryBack`
- 知识库：`fore-end/repository_vue/knowledge`
- 项目文档：`fore-end/repository_vue/docs`

## 功能概览

- 普通聊天：前端调用后端 `/api/ai/chat`
- 固定文档 RAG：后端读取本地 `knowledge` 目录中的 Markdown 文档，通过 `/api/ai/rag-simple` 提供回答
- 向量 RAG：后端调用 Ollama embedding 模型完成检索，通过 `/api/ai/rag` 提供回答
- 前端统一展示回答内容、知识库命中状态和来源片段

## 技术栈

### 前端

- Vue 3
- TypeScript
- Vite

### 后端

- Java 17
- Spring Boot 4
- Maven

### 本地 AI

- Ollama
- 聊天模型：`deepseek-r1:7b`
- 向量模型：`bge-m3:latest`

## 运行前准备

启动前请确认以下环境可用：

1. 已安装 Node.js
2. 已安装 JDK 17
3. 已安装 Maven，或使用项目自带的 Maven Wrapper
4. 本机已启动 Ollama
5. Ollama 中已准备好以下模型：
   - `deepseek-r1:7b`
   - `bge-m3:latest`

后端当前默认配置位于 `back-end/repositoryBack/src/main/resources/application.properties`：

- 服务端口：`8080`
- Ollama 地址：`http://127.0.0.1:11434`
- 知识库目录：`P:/AIprogect/repository/fore-end/repository_vue/knowledge`

## 启动方式

### 1. 启动后端

进入后端目录：

```powershell
cd P:\AIprogect\repository\back-end\repositoryBack
```

使用 Maven Wrapper 启动：

```powershell
.\mvnw.cmd spring-boot:run
```

或使用本机 Maven：

```powershell
mvn spring-boot:run
```

启动后默认监听：

```text
http://127.0.0.1:8080
```

### 2. 启动前端

进入前端目录：

```powershell
cd P:\AIprogect\repository\fore-end\repository_vue
```

安装依赖：

```powershell
npm install
```

启动开发服务：

```powershell
npm run dev
```

前端使用 Vite 代理 `/api` 到本地后端 `http://127.0.0.1:8080`。

## 后端接口

当前后端提供以下接口：

- `POST /api/ai/chat`
- `POST /api/ai/rag-simple`
- `POST /api/ai/rag`

请求体统一为：

```json
{
  "question": "你的问题"
}
```

## 知识库说明

本项目的知识库文档当前放在：

```text
fore-end/repository_vue/knowledge
```

后端会根据配置读取该目录中的 `.md` 文件，用于简单 RAG 和向量 RAG 检索。

建议优先阅读以下文档了解项目接入方式：

1. `fore-end/repository_vue/docs/README.md`
2. `fore-end/repository_vue/docs/01-项目现状总览.md`
3. `fore-end/repository_vue/docs/02-接口与页面说明.md`
4. `fore-end/repository_vue/docs/04-测试与排障手册.md`

## 当前状态

当前仓库已包含：

- 前端页面工程
- 后端 AI / RAG 接口
- 本地知识库示例文档
- 项目接入与排障说明文档

仓库根目录的这个 `README.md` 作为总览入口，前后端和知识库的详细说明请继续查看各子目录中的文档。
