<script setup lang="ts">
import { ref } from 'vue'
import { aiChat, aiRag, aiRagSimple, type RagSourceItem } from './api/ai'

const question = ref('')
const answer = ref('等待提问')
const errorMessage = ref('')
const isLoading = ref(false)
const sources = ref<RagSourceItem[]>([])
const mode = ref<'chat' | 'rag-simple' | 'rag'>('rag')
const knowledgeHit = ref<boolean | null>(null)

async function submitQuestion() {
  const trimmedQuestion = question.value.trim()
  if (!trimmedQuestion || isLoading.value) {
    return
  }

  isLoading.value = true
  errorMessage.value = ''
  sources.value = []
  knowledgeHit.value = null

  try {
    if (mode.value === 'chat') {
      const response = await aiChat(trimmedQuestion)
      answer.value = response.answer
      knowledgeHit.value = null
      return
    }

    const response =
      mode.value === 'rag'
        ? await aiRag(trimmedQuestion)
        : await aiRagSimple(trimmedQuestion)
    answer.value = response.answer
    sources.value = response.sources
    knowledgeHit.value = response.knowledgeHit
  } catch (error) {
    answer.value = '当前没有拿到模型回复。'
    errorMessage.value = error instanceof Error ? error.message : '调用失败'
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <main class="page-shell">
    <section class="hero-panel">
      <p class="eyebrow">Local AI Bridge</p>
      <h1>Vue 前端已准备好接入本地 Ollama</h1>
      <p class="hero-copy">
        当前页面支持普通对话、固定文档知识库和向量检索知识库。第二阶段会先对问题和
        文档切块做 embedding，再筛出命中片段交给本地 Ollama 回答。
      </p>
      <div class="status-row">
        <span class="status-chip">前端: Vite + Vue 3</span>
        <span class="status-chip">后端: Spring Boot</span>
        <span class="status-chip">模型: deepseek-r1:7b</span>
        <span class="status-chip">Embedding: bge-m3:latest</span>
      </div>
    </section>

    <section class="chat-panel">
      <div class="mode-switch">
        <button
          class="mode-button"
          :class="{ active: mode === 'rag' }"
          type="button"
          @click="mode = 'rag'"
        >
          向量知识库
        </button>
        <button
          class="mode-button"
          :class="{ active: mode === 'rag-simple' }"
          type="button"
          @click="mode = 'rag-simple'"
        >
          固定文档
        </button>
        <button
          class="mode-button"
          :class="{ active: mode === 'chat' }"
          type="button"
          @click="mode = 'chat'"
        >
          普通对话
        </button>
      </div>

      <label class="field-label" for="question">问题内容</label>
      <textarea
        id="question"
        v-model="question"
        class="question-box"
        rows="6"
        :placeholder="
          mode === 'rag'
            ? '例如：知识库方案里推荐的第二阶段做法是什么？'
            : mode === 'rag-simple'
            ? '例如：这两个接入方案文档分别讲了什么？'
            : '例如：请解释一下 Spring Boot 如何调用 Ollama。'
        "
      />

      <div class="action-row">
        <button class="submit-button" type="button" :disabled="isLoading" @click="submitQuestion">
          {{
            isLoading
              ? '请求中...'
              : mode === 'chat'
                ? '发送到 Ollama'
                : mode === 'rag'
                  ? '发送到向量知识库'
                  : '发送到固定文档'
          }}
        </button>
        <span class="hint-text">
          {{
            mode === 'rag'
              ? '向量知识库会先做 embedding 检索，再只把命中的片段交给模型。'
              : mode === 'rag-simple'
                ? '固定文档模式会直接拼接 knowledge 目录下的 Markdown 文档。'
                : '普通对话模式不会读取本地知识库。'
          }}
        </span>
      </div>

      <div class="result-card">
        <div class="result-header">
          <h2>模型回复</h2>
          <span v-if="errorMessage" class="error-badge">调用失败</span>
          <span
            v-else-if="mode !== 'chat' && knowledgeHit !== null"
            class="hit-badge"
            :class="{ miss: !knowledgeHit }"
          >
            {{ knowledgeHit ? '命中知识库' : '未命中知识库' }}
          </span>
        </div>
        <p class="result-text">{{ answer }}</p>
        <p v-if="errorMessage" class="error-text">{{ errorMessage }}</p>
      </div>

      <div v-if="mode !== 'chat' && sources.length" class="source-card">
        <div class="result-header">
          <h2>引用来源</h2>
          <span class="source-count">共 {{ sources.length }} 份文档</span>
        </div>
        <ol class="source-list">
          <li v-for="source in sources" :key="source.fileName" class="source-item">
            <p class="source-file">
              {{ source.fileName }}
              <span v-if="source.score !== null" class="source-score">score {{ source.score }}</span>
            </p>
            <p class="source-snippet">{{ source.snippet }}</p>
          </li>
        </ol>
      </div>
    </section>
  </main>
</template>
