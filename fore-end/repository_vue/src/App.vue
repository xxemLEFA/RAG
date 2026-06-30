<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  aiChat,
  aiRag,
  aiRagSimple,
  compareMarkdownDirectories,
  fetchKnowledgeOverview,
  reindexKnowledge,
  type KnowledgeOverviewResponse,
  type MarkdownCompareLine,
  type MarkdownCompareResponse,
  type MarkdownModifiedFileItem,
  type RagSourceItem,
} from './api/ai'

type WorkspaceMode = 'rag' | 'markdown-compare'
type ChatMode = 'chat' | 'rag-simple' | 'rag'

const workspaceMode = ref<WorkspaceMode>('rag')

const question = ref('')
const answer = ref('等待提问')
const errorMessage = ref('')
const isLoading = ref(false)
const sources = ref<RagSourceItem[]>([])
const mode = ref<ChatMode>('rag')
const knowledgeHit = ref<boolean | null>(null)
const knowledgeOverview = ref<KnowledgeOverviewResponse | null>(null)
const knowledgeError = ref('')
const isLoadingKnowledge = ref(false)
const isReindexing = ref(false)

const sourceDir = ref('P:/AIprogect/work-sync-md')
const targetDir = ref('P:/AIprogect/repository/fore-end/repository_vue/docs')
const markdownCompareResult = ref<MarkdownCompareResponse | null>(null)
const markdownCompareError = ref('')
const isComparingMarkdown = ref(false)
const expandedFiles = ref<string[]>([])

const totalMarkdownDifferences = computed(() => {
  const result = markdownCompareResult.value
  if (!result) {
    return 0
  }
  return result.addedFiles.length + result.removedFiles.length + result.modifiedFiles.length
})

function formatFileSize(sizeBytes: number) {
  if (sizeBytes < 1024) {
    return `${sizeBytes} B`
  }
  if (sizeBytes < 1024 * 1024) {
    return `${(sizeBytes / 1024).toFixed(1)} KB`
  }
  return `${(sizeBytes / (1024 * 1024)).toFixed(1)} MB`
}

function formatDiffLinePrefix(line: MarkdownCompareLine) {
  if (line.type === 'add') {
    return '+'
  }
  if (line.type === 'remove') {
    return '-'
  }
  return ' '
}

function isFileExpanded(relativePath: string) {
  return expandedFiles.value.includes(relativePath)
}

function toggleFileExpanded(relativePath: string) {
  if (isFileExpanded(relativePath)) {
    expandedFiles.value = expandedFiles.value.filter((item) => item !== relativePath)
    return
  }
  expandedFiles.value = [...expandedFiles.value, relativePath]
}

async function loadKnowledgeOverview() {
  isLoadingKnowledge.value = true
  knowledgeError.value = ''

  try {
    knowledgeOverview.value = await fetchKnowledgeOverview()
  } catch (error) {
    knowledgeError.value = error instanceof Error ? error.message : '读取知识库状态失败'
  } finally {
    isLoadingKnowledge.value = false
  }
}

async function handleReindex() {
  if (isReindexing.value) {
    return
  }

  isReindexing.value = true
  knowledgeError.value = ''

  try {
    knowledgeOverview.value = await reindexKnowledge()
  } catch (error) {
    knowledgeError.value = error instanceof Error ? error.message : '重建索引失败'
  } finally {
    isReindexing.value = false
  }
}

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
      mode.value === 'rag' ? await aiRag(trimmedQuestion) : await aiRagSimple(trimmedQuestion)
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

async function handleCompareMarkdown() {
  const trimmedSourceDir = sourceDir.value.trim()
  const trimmedTargetDir = targetDir.value.trim()
  if (!trimmedSourceDir || !trimmedTargetDir || isComparingMarkdown.value) {
    return
  }

  isComparingMarkdown.value = true
  markdownCompareError.value = ''

  try {
    const result = await compareMarkdownDirectories(trimmedSourceDir, trimmedTargetDir)
    markdownCompareResult.value = result
    expandedFiles.value = result.modifiedFiles.slice(0, 1).map((item) => item.relativePath)
  } catch (error) {
    markdownCompareResult.value = null
    expandedFiles.value = []
    markdownCompareError.value = error instanceof Error ? error.message : '目录对比失败'
  } finally {
    isComparingMarkdown.value = false
  }
}

function addedSummaryLabel(result: MarkdownCompareResponse) {
  return `新增 ${result.addedFiles.length}`
}

function removedSummaryLabel(result: MarkdownCompareResponse) {
  return `缺失 ${result.removedFiles.length}`
}

function modifiedSummaryLabel(result: MarkdownCompareResponse) {
  return `修改 ${result.modifiedFiles.length}`
}

function changeSummary(file: MarkdownModifiedFileItem) {
  return `+${file.additions} / -${file.deletions} · 源 ${file.sourceLineCount} 行 · 本地 ${file.targetLineCount} 行`
}

onMounted(() => {
  void loadKnowledgeOverview()
})
</script>

<template>
  <main class="page-shell">
    <section class="hero-panel">
      <p class="eyebrow">Local AI Workspace</p>
      <h1>RAG 调试台 + Markdown 同步对比</h1>
      <p class="hero-copy">
        这套页面现在分成两个工作区：一边保留本地 Ollama / RAG 调试，另一边专门拿来对比两个目录里的
        Markdown 文件，方便你同步工作电脑和本地资料后快速检查差异。
      </p>
      <div class="status-row">
        <span class="status-chip">前端: Vite + Vue 3</span>
        <span class="status-chip">后端: Spring Boot</span>
        <span class="status-chip">模型: deepseek-r1:7b</span>
        <span class="status-chip">工具: 递归 MD 对比</span>
      </div>
    </section>

    <section class="workspace-switch">
      <button
        class="workspace-button"
        :class="{ active: workspaceMode === 'rag' }"
        type="button"
        @click="workspaceMode = 'rag'"
      >
        RAG 助手
      </button>
      <button
        class="workspace-button"
        :class="{ active: workspaceMode === 'markdown-compare' }"
        type="button"
        @click="workspaceMode = 'markdown-compare'"
      >
        Markdown 对比
      </button>
    </section>

    <section v-if="workspaceMode === 'rag'" class="chat-panel">
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
              ? '例如：这两份接入方案文档分别讲了什么？'
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
              ? '先做 embedding 检索，再只把命中的片段交给模型。'
              : mode === 'rag-simple'
                ? '直接拼接 knowledge 目录中的 Markdown 文档。'
                : '不会读取本地知识库，只做普通问答。'
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

      <div class="knowledge-card">
        <div class="result-header">
          <h2>知识库状态</h2>
          <div class="knowledge-actions">
            <button
              class="ghost-button"
              type="button"
              :disabled="isLoadingKnowledge"
              @click="loadKnowledgeOverview"
            >
              {{ isLoadingKnowledge ? '读取中...' : '刷新状态' }}
            </button>
            <button
              class="ghost-button strong"
              type="button"
              :disabled="isReindexing"
              @click="handleReindex"
            >
              {{ isReindexing ? '重建中...' : '重建向量索引' }}
            </button>
          </div>
        </div>

        <p v-if="knowledgeOverview" class="knowledge-meta">
          当前目录 <code>{{ knowledgeOverview.baseDir }}</code>，共匹配
          {{ knowledgeOverview.totalMatchedFiles }} 个文件。向量模式当前使用前
          {{ knowledgeOverview.vectorFileLimit }} 个文件，累计
          {{ knowledgeOverview.totalVectorChunks }} 个 chunk。
        </p>
        <p v-if="knowledgeOverview" class="knowledge-meta secondary">
          文件规则 <code>{{ knowledgeOverview.filePattern }}</code>，固定文档上限
          {{ knowledgeOverview.simpleFileLimit }}，向量 chunk 上限
          {{ knowledgeOverview.vectorChunkLimit }}。
        </p>
        <p v-if="knowledgeError" class="error-text">{{ knowledgeError }}</p>

        <ul v-if="knowledgeOverview?.files.length" class="knowledge-list">
          <li v-for="file in knowledgeOverview.files" :key="file.fileName" class="knowledge-item">
            <div class="knowledge-item-head">
              <p class="source-file">{{ file.fileName }}</p>
              <div class="knowledge-badges">
                <span v-if="file.usedBySimpleRag" class="mini-badge">固定文档</span>
                <span v-if="file.usedByVectorRag" class="mini-badge accent">
                  向量 {{ file.chunkCount }} chunk
                </span>
              </div>
            </div>
            <p class="knowledge-detail">{{ formatFileSize(file.sizeBytes) }} · {{ file.lastModified }}</p>
          </li>
        </ul>
      </div>
    </section>

    <section v-else class="compare-panel">
      <div class="compare-head">
        <div>
          <p class="eyebrow small">Markdown Sync Diff</p>
          <h2>递归对比两个目录中的 `*.md` 文件</h2>
        </div>
        <p class="hint-text compare-hint">
          适合拿“刚从工作电脑下载的目录”对比“本地已有目录”，自动识别新增、缺失和内容修改。
        </p>
      </div>

      <div class="compare-form">
        <label class="field-label" for="sourceDir">下载目录 / 来源目录</label>
        <input id="sourceDir" v-model="sourceDir" class="path-input" type="text" spellcheck="false" />

        <label class="field-label" for="targetDir">本地目录 / 目标目录</label>
        <input id="targetDir" v-model="targetDir" class="path-input" type="text" spellcheck="false" />

        <div class="action-row">
          <button
            class="submit-button"
            type="button"
            :disabled="isComparingMarkdown"
            @click="handleCompareMarkdown"
          >
            {{ isComparingMarkdown ? '对比中...' : '开始对比 Markdown' }}
          </button>
          <span class="hint-text">只递归扫描 `.md` 文件，路径按相对目录对齐。</span>
        </div>
      </div>

      <p v-if="markdownCompareError" class="error-text compare-error">{{ markdownCompareError }}</p>

      <template v-if="markdownCompareResult">
        <div class="compare-summary-grid">
          <article class="summary-card">
            <p class="summary-label">来源目录</p>
            <p class="summary-value path">{{ markdownCompareResult.sourceDir }}</p>
            <p class="summary-meta">{{ markdownCompareResult.sourceFileCount }} 个 Markdown 文件</p>
          </article>
          <article class="summary-card">
            <p class="summary-label">本地目录</p>
            <p class="summary-value path">{{ markdownCompareResult.targetDir }}</p>
            <p class="summary-meta">{{ markdownCompareResult.targetFileCount }} 个 Markdown 文件</p>
          </article>
          <article class="summary-card accent">
            <p class="summary-label">差异总数</p>
            <p class="summary-value">{{ totalMarkdownDifferences }}</p>
            <p class="summary-meta">{{ markdownCompareResult.unchangedCount }} 个文件完全一致</p>
          </article>
        </div>

        <div class="summary-pill-row">
          <span class="summary-pill add">{{ addedSummaryLabel(markdownCompareResult) }}</span>
          <span class="summary-pill remove">{{ removedSummaryLabel(markdownCompareResult) }}</span>
          <span class="summary-pill modify">{{ modifiedSummaryLabel(markdownCompareResult) }}</span>
        </div>

        <div v-if="markdownCompareResult.addedFiles.length" class="diff-list-card">
          <div class="result-header">
            <h2>来源目录新增</h2>
            <span class="source-count">{{ markdownCompareResult.addedFiles.length }} 个</span>
          </div>
          <ul class="file-list">
            <li v-for="file in markdownCompareResult.addedFiles" :key="file">{{ file }}</li>
          </ul>
        </div>

        <div v-if="markdownCompareResult.removedFiles.length" class="diff-list-card">
          <div class="result-header">
            <h2>本地目录缺失</h2>
            <span class="source-count">{{ markdownCompareResult.removedFiles.length }} 个</span>
          </div>
          <ul class="file-list">
            <li v-for="file in markdownCompareResult.removedFiles" :key="file">{{ file }}</li>
          </ul>
        </div>

        <div v-if="markdownCompareResult.modifiedFiles.length" class="modified-card">
          <div class="result-header">
            <h2>内容有变化的文件</h2>
            <span class="source-count">{{ markdownCompareResult.modifiedFiles.length }} 个</span>
          </div>

          <article
            v-for="file in markdownCompareResult.modifiedFiles"
            :key="file.relativePath"
            class="modified-item"
          >
            <button class="modified-toggle" type="button" @click="toggleFileExpanded(file.relativePath)">
              <span>
                <strong>{{ file.relativePath }}</strong>
                <span class="modified-meta">{{ changeSummary(file) }}</span>
              </span>
              <span class="toggle-indicator">{{ isFileExpanded(file.relativePath) ? '收起' : '展开' }}</span>
            </button>

            <div v-if="isFileExpanded(file.relativePath)" class="hunk-list">
              <section v-for="(hunk, hunkIndex) in file.hunks" :key="hunkIndex" class="hunk-card">
                <p class="hunk-title">
                  片段 {{ hunkIndex + 1 }} · 源文件从第 {{ hunk.sourceStartLine }} 行开始 · 本地文件从第
                  {{ hunk.targetStartLine }} 行开始
                </p>
                <div class="diff-code">
                  <div
                    v-for="(line, lineIndex) in hunk.lines"
                    :key="`${hunkIndex}-${lineIndex}`"
                    class="diff-line"
                    :class="line.type"
                  >
                    <span class="line-number">{{ line.sourceLineNumber ?? '' }}</span>
                    <span class="line-number">{{ line.targetLineNumber ?? '' }}</span>
                    <span class="line-prefix">{{ formatDiffLinePrefix(line) }}</span>
                    <code class="line-content">{{ line.content || ' ' }}</code>
                  </div>
                </div>
              </section>
            </div>
          </article>
        </div>
      </template>
    </section>
  </main>
</template>
