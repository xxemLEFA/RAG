<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  aiChat,
  aiRag,
  aiRagSimple,
  browseDirectory,
  compareMarkdownDirectories,
  fetchKnowledgeOverview,
  reindexKnowledge,
  syncMarkdownFiles,
  type KnowledgeOverviewResponse,
  type MarkdownCompareLine,
  type MarkdownCompareResponse,
  type MarkdownModifiedFileItem,
  type MarkdownSyncDestination,
  type RagSourceItem,
} from './api/ai'

type WorkspaceMode = 'rag' | 'markdown-compare'
type ChatMode = 'chat' | 'rag-simple' | 'rag'

interface SyncDialogState {
  title: string
  description: string
  destination: MarkdownSyncDestination
  relativePaths: string[]
}

interface PathDialogState {
  target: 'source' | 'target'
  title: string
  value: string
}

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
const markdownSyncMessage = ref('')
const isComparingMarkdown = ref(false)
const isSyncingMarkdown = ref(false)
const activeDirectoryPicker = ref<'source' | 'target' | null>(null)
const expandedFiles = ref<string[]>([])
const selectedAddedFiles = ref<string[]>([])
const selectedModifiedFiles = ref<string[]>([])
const syncDialog = ref<SyncDialogState | null>(null)
const pathDialog = ref<PathDialogState | null>(null)

const totalMarkdownDifferences = computed(() => {
  const result = markdownCompareResult.value
  if (!result) {
    return 0
  }
  return result.addedFiles.length + result.removedFiles.length + result.modifiedFiles.length
})

const modifiedFilePathSet = computed(() => {
  return new Set(markdownCompareResult.value?.modifiedFiles.map((item) => item.relativePath) ?? [])
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

function isAddedSelected(relativePath: string) {
  return selectedAddedFiles.value.includes(relativePath)
}

function isModifiedSelected(relativePath: string) {
  return selectedModifiedFiles.value.includes(relativePath)
}

function toggleAddedSelection(relativePath: string) {
  selectedAddedFiles.value = isAddedSelected(relativePath)
    ? selectedAddedFiles.value.filter((item) => item !== relativePath)
    : [...selectedAddedFiles.value, relativePath]
}

function toggleModifiedSelection(relativePath: string) {
  selectedModifiedFiles.value = isModifiedSelected(relativePath)
    ? selectedModifiedFiles.value.filter((item) => item !== relativePath)
    : [...selectedModifiedFiles.value, relativePath]
}

function selectAllAddedFiles() {
  selectedAddedFiles.value = markdownCompareResult.value?.addedFiles.slice() ?? []
}

function clearAddedSelection() {
  selectedAddedFiles.value = []
}

function selectAllModifiedFiles() {
  selectedModifiedFiles.value = markdownCompareResult.value?.modifiedFiles.map((item) => item.relativePath) ?? []
}

function clearModifiedSelection() {
  selectedModifiedFiles.value = []
}

function syncResultSelections(result: MarkdownCompareResponse) {
  const addedSet = new Set(result.addedFiles)
  selectedAddedFiles.value = selectedAddedFiles.value.filter((item) => addedSet.has(item))
  selectedModifiedFiles.value = selectedModifiedFiles.value.filter((item) => modifiedFilePathSet.value.has(item))
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
  markdownSyncMessage.value = ''

  try {
    const result = await compareMarkdownDirectories(trimmedSourceDir, trimmedTargetDir)
    markdownCompareResult.value = result
    expandedFiles.value = result.modifiedFiles.slice(0, 1).map((item) => item.relativePath)
    syncResultSelections(result)
  } catch (error) {
    markdownCompareResult.value = null
    expandedFiles.value = []
    selectedAddedFiles.value = []
    selectedModifiedFiles.value = []
    markdownCompareError.value = error instanceof Error ? error.message : '目录对比失败'
  } finally {
    isComparingMarkdown.value = false
  }
}

async function pickDirectory(target: 'source' | 'target') {
  if (activeDirectoryPicker.value) {
    return
  }

  activeDirectoryPicker.value = target
  markdownCompareError.value = ''

  try {
    const initialDir = target === 'source' ? sourceDir.value.trim() : targetDir.value.trim()
    const response = await browseDirectory(
      initialDir,
      target === 'source' ? '选择来源目录' : '选择目标目录',
    )

    if (target === 'source') {
      sourceDir.value = response.selectedDir
    } else {
      targetDir.value = response.selectedDir
    }
  } catch (error) {
    markdownCompareError.value = error instanceof Error ? error.message : '打开目录选择框失败'
  } finally {
    activeDirectoryPicker.value = null
  }
}

function openPathDialog(target: 'source' | 'target') {
  pathDialog.value = {
    target,
    title: target === 'source' ? '输入来源目录' : '输入目标目录',
    value: target === 'source' ? sourceDir.value : targetDir.value,
  }
}

function closePathDialog() {
  if (activeDirectoryPicker.value) {
    return
  }
  pathDialog.value = null
}

function confirmPathDialog() {
  const currentDialog = pathDialog.value
  if (!currentDialog) {
    return
  }

  const trimmedValue = currentDialog.value.trim()
  if (!trimmedValue) {
    markdownCompareError.value = '目录路径不能为空'
    return
  }

  if (currentDialog.target === 'source') {
    sourceDir.value = trimmedValue
  } else {
    targetDir.value = trimmedValue
  }

  markdownCompareError.value = ''
  pathDialog.value = null
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

function openSyncDialogForAddedFiles() {
  if (!selectedAddedFiles.value.length) {
    markdownCompareError.value = '请先勾选要同步到本地目录的新增文件'
    return
  }

  syncDialog.value = {
    title: '确认同步新增文件',
    description: `将把 ${selectedAddedFiles.value.length} 个来源目录中的新增文件复制到本地目录，并覆盖同名文件。`,
    destination: 'target',
    relativePaths: selectedAddedFiles.value.slice(),
  }
}

function openSyncDialogForModifiedFiles(destination: MarkdownSyncDestination) {
  if (!selectedModifiedFiles.value.length) {
    markdownCompareError.value = '请先勾选要同步的内容变更文件'
    return
  }

  syncDialog.value = {
    title: destination === 'target' ? '确认用来源目录覆盖本地目录' : '确认用本地目录覆盖来源目录',
    description:
      destination === 'target'
        ? `将把 ${selectedModifiedFiles.value.length} 个已修改文件从来源目录复制到本地目录。`
        : `将把 ${selectedModifiedFiles.value.length} 个已修改文件从本地目录复制到来源目录。`,
    destination,
    relativePaths: selectedModifiedFiles.value.slice(),
  }
}

function closeSyncDialog() {
  if (isSyncingMarkdown.value) {
    return
  }
  syncDialog.value = null
}

async function confirmSync() {
  const currentDialog = syncDialog.value
  if (!currentDialog || isSyncingMarkdown.value) {
    return
  }

  isSyncingMarkdown.value = true
  markdownCompareError.value = ''
  markdownSyncMessage.value = ''

  try {
    const response = await syncMarkdownFiles(
      sourceDir.value.trim(),
      targetDir.value.trim(),
      currentDialog.destination,
      currentDialog.relativePaths,
    )

    syncDialog.value = null
    markdownSyncMessage.value = `已同步 ${response.syncedFiles.length} 个文件到 ${response.copiedTo}`

    if (currentDialog.destination === 'target') {
      selectedAddedFiles.value = selectedAddedFiles.value.filter(
        (item) => !currentDialog.relativePaths.includes(item),
      )
    }
    selectedModifiedFiles.value = selectedModifiedFiles.value.filter(
      (item) => !currentDialog.relativePaths.includes(item),
    )

    await handleCompareMarkdown()
  } catch (error) {
    markdownCompareError.value = error instanceof Error ? error.message : '同步失败'
  } finally {
    isSyncingMarkdown.value = false
  }
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
        <div class="path-row">
          <input id="sourceDir" v-model="sourceDir" class="path-input" type="text" spellcheck="false" />
          <div class="path-button-group">
            <button class="ghost-button path-button" type="button" @click="openPathDialog('source')">
              输入路径
            </button>
            <button
              class="ghost-button path-button"
              type="button"
              :disabled="!!activeDirectoryPicker"
              @click="pickDirectory('source')"
            >
              {{ activeDirectoryPicker === 'source' ? '选择中...' : '选择文件夹' }}
            </button>
          </div>
        </div>

        <label class="field-label" for="targetDir">本地目录 / 目标目录</label>
        <div class="path-row">
          <input id="targetDir" v-model="targetDir" class="path-input" type="text" spellcheck="false" />
          <div class="path-button-group">
            <button class="ghost-button path-button" type="button" @click="openPathDialog('target')">
              输入路径
            </button>
            <button
              class="ghost-button path-button"
              type="button"
              :disabled="!!activeDirectoryPicker"
              @click="pickDirectory('target')"
            >
              {{ activeDirectoryPicker === 'target' ? '选择中...' : '选择文件夹' }}
            </button>
          </div>
        </div>

        <div class="action-row">
          <button
            class="submit-button"
            type="button"
            :disabled="isComparingMarkdown || isSyncingMarkdown"
            @click="handleCompareMarkdown"
          >
            {{ isComparingMarkdown ? '对比中...' : '开始对比 Markdown' }}
          </button>
          <span class="hint-text">只递归扫描 `.md` 文件，路径按相对目录对齐。</span>
        </div>
      </div>

      <p v-if="markdownCompareError" class="error-text compare-error">{{ markdownCompareError }}</p>
      <p v-if="markdownSyncMessage" class="success-text">{{ markdownSyncMessage }}</p>

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
          <div class="section-actions">
            <button class="ghost-button" type="button" @click="selectAllAddedFiles">全选</button>
            <button class="ghost-button" type="button" @click="clearAddedSelection">清空</button>
            <button
              class="ghost-button strong"
              type="button"
              :disabled="!selectedAddedFiles.length || isSyncingMarkdown"
              @click="openSyncDialogForAddedFiles"
            >
              同步到本地目录
            </button>
          </div>
          <ul class="file-list selectable">
            <li v-for="file in markdownCompareResult.addedFiles" :key="file">
              <label class="file-check-row">
                <input
                  :checked="isAddedSelected(file)"
                  class="file-checkbox"
                  type="checkbox"
                  @change="toggleAddedSelection(file)"
                />
                <span>{{ file }}</span>
              </label>
            </li>
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
          <div class="section-actions">
            <button class="ghost-button" type="button" @click="selectAllModifiedFiles">全选</button>
            <button class="ghost-button" type="button" @click="clearModifiedSelection">清空</button>
            <button
              class="ghost-button strong"
              type="button"
              :disabled="!selectedModifiedFiles.length || isSyncingMarkdown"
              @click="openSyncDialogForModifiedFiles('target')"
            >
              用来源覆盖本地
            </button>
            <button
              class="ghost-button"
              type="button"
              :disabled="!selectedModifiedFiles.length || isSyncingMarkdown"
              @click="openSyncDialogForModifiedFiles('source')"
            >
              用本地覆盖来源
            </button>
          </div>

          <article
            v-for="file in markdownCompareResult.modifiedFiles"
            :key="file.relativePath"
            class="modified-item"
          >
            <div class="modified-heading">
              <label class="file-check-row">
                <input
                  :checked="isModifiedSelected(file.relativePath)"
                  class="file-checkbox"
                  type="checkbox"
                  @change="toggleModifiedSelection(file.relativePath)"
                />
                <span>
                  <strong>{{ file.relativePath }}</strong>
                  <span class="modified-meta">{{ changeSummary(file) }}</span>
                </span>
              </label>
              <button class="inline-toggle" type="button" @click="toggleFileExpanded(file.relativePath)">
                {{ isFileExpanded(file.relativePath) ? '收起差异' : '展开差异' }}
              </button>
            </div>

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

    <div v-if="syncDialog" class="modal-backdrop" @click.self="closeSyncDialog">
      <section class="modal-card">
        <p class="eyebrow small">Second Confirmation</p>
        <h2>{{ syncDialog.title }}</h2>
        <p class="modal-copy">{{ syncDialog.description }}</p>
        <p class="modal-copy">
          目标目录:
          <code>{{ syncDialog.destination === 'target' ? targetDir.trim() : sourceDir.trim() }}</code>
        </p>
        <p class="modal-copy">
          以下 {{ syncDialog.relativePaths.length }} 个文件会被复制并覆盖目标中的同名文件。
        </p>
        <ul class="modal-file-list">
          <li v-for="file in syncDialog.relativePaths" :key="file">{{ file }}</li>
        </ul>
        <div class="modal-actions">
          <button class="ghost-button" type="button" :disabled="isSyncingMarkdown" @click="closeSyncDialog">
            取消
          </button>
          <button class="submit-button" type="button" :disabled="isSyncingMarkdown" @click="confirmSync">
            {{ isSyncingMarkdown ? '同步中...' : '确认同步' }}
          </button>
        </div>
      </section>
    </div>

    <div v-if="pathDialog" class="modal-backdrop" @click.self="closePathDialog">
      <section class="modal-card compact">
        <p class="eyebrow small">Path Input</p>
        <h2>{{ pathDialog.title }}</h2>
        <p class="modal-copy">可以直接粘贴完整目录路径，也可以继续使用系统文件夹选择器。</p>
        <input
          v-model="pathDialog.value"
          class="path-input modal-path-input"
          type="text"
          spellcheck="false"
          placeholder="例如：P:/AIprogect/知识库/测试/2006.6.30版本知识库"
          @keydown.enter="confirmPathDialog"
        />
        <div class="modal-actions split">
          <button class="ghost-button" type="button" :disabled="!!activeDirectoryPicker" @click="closePathDialog">
            取消
          </button>
          <button
            class="ghost-button"
            type="button"
            :disabled="!!activeDirectoryPicker"
            @click="pickDirectory(pathDialog.target)"
          >
            {{ activeDirectoryPicker === pathDialog.target ? '选择中...' : '系统选择文件夹' }}
          </button>
          <button class="submit-button" type="button" @click="confirmPathDialog">确认使用这个路径</button>
        </div>
      </section>
    </div>
  </main>
</template>
