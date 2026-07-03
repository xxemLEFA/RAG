export interface AiChatResponse {
  answer: string
}

export interface RagSourceItem {
  fileName: string
  snippet: string
  score: number | null
}

export interface RagMatchItem {
  fileName: string
  chunkIndex: number
  content: string
  score: number | null
}

export interface AiRagResponse {
  answer: string
  sources: RagSourceItem[]
  knowledgeHit: boolean
  matches: RagMatchItem[]
}

export interface KnowledgeFileItem {
  fileName: string
  sizeBytes: number
  lastModified: string
  usedBySimpleRag: boolean
  usedByVectorRag: boolean
  chunkCount: number
}

export interface QdrantCollectionStatus {
  enabled: boolean
  baseUrl: string
  collectionName: string
  collectionExists: boolean
  indexedPoints: number | null
  statusMessage: string
}

export interface KnowledgeOverviewResponse {
  baseDir: string
  filePattern: string
  vectorBackend: string
  simpleFileLimit: number
  vectorFileLimit: number
  vectorChunkLimit: number
  totalMatchedFiles: number
  totalVectorChunks: number
  effectiveVectorChunks: number
  vectorChunkLimitReached: boolean
  qdrantStatus: QdrantCollectionStatus | null
  files: KnowledgeFileItem[]
}

export interface MarkdownCompareLine {
  type: 'context' | 'add' | 'remove'
  sourceLineNumber: number | null
  targetLineNumber: number | null
  content: string
}

export interface MarkdownCompareHunk {
  sourceStartLine: number
  targetStartLine: number
  lines: MarkdownCompareLine[]
}

export interface MarkdownModifiedFileItem {
  relativePath: string
  sourceLineCount: number
  targetLineCount: number
  additions: number
  deletions: number
  hunks: MarkdownCompareHunk[]
}

export interface MarkdownCompareResponse {
  sourceDir: string
  targetDir: string
  sourceFileCount: number
  targetFileCount: number
  unchangedCount: number
  addedFiles: string[]
  removedFiles: string[]
  modifiedFiles: MarkdownModifiedFileItem[]
}

export type MarkdownSyncDestination = 'source' | 'target'

export interface MarkdownSyncResponse {
  sourceDir: string
  targetDir: string
  copiedFrom: string
  copiedTo: string
  syncedFiles: string[]
}

export interface DirectoryBrowseResponse {
  selectedDir: string
}

async function postJson<T>(url: string, payload: object): Promise<T> {
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  })

  if (!response.ok) {
    let message = '请求失败'

    try {
      const errorBody = await response.json()
      message = errorBody.detail ?? errorBody.message ?? message
    } catch {
      message = response.statusText || message
    }

    throw new Error(message)
  }

  return response.json() as Promise<T>
}

export async function aiChat(question: string): Promise<AiChatResponse> {
  return postJson<AiChatResponse>('/api/ai/chat', { question })
}

export async function aiRagSimple(question: string): Promise<AiRagResponse> {
  return postJson<AiRagResponse>('/api/ai/rag-simple', { question })
}

export async function aiRag(question: string): Promise<AiRagResponse> {
  return postJson<AiRagResponse>('/api/ai/rag', { question })
}

export async function fetchKnowledgeOverview(): Promise<KnowledgeOverviewResponse> {
  const response = await fetch('/api/ai/knowledge')
  if (!response.ok) {
    throw new Error(response.statusText || '读取知识库状态失败')
  }
  return response.json() as Promise<KnowledgeOverviewResponse>
}

export async function reindexKnowledge(): Promise<KnowledgeOverviewResponse> {
  return postJson<KnowledgeOverviewResponse>('/api/ai/knowledge/reindex', {})
}

export async function compareMarkdownDirectories(
  sourceDir: string,
  targetDir: string,
): Promise<MarkdownCompareResponse> {
  return postJson<MarkdownCompareResponse>('/api/tools/markdown/compare', {
    sourceDir,
    targetDir,
  })
}

export async function syncMarkdownFiles(
  sourceDir: string,
  targetDir: string,
  destination: MarkdownSyncDestination,
  relativePaths: string[],
): Promise<MarkdownSyncResponse> {
  return postJson<MarkdownSyncResponse>('/api/tools/markdown/sync', {
    sourceDir,
    targetDir,
    destination,
    relativePaths,
  })
}

export async function browseDirectory(
  initialDir: string,
  dialogTitle: string,
): Promise<DirectoryBrowseResponse> {
  return postJson<DirectoryBrowseResponse>('/api/tools/markdown/browse-directory', {
    initialDir,
    dialogTitle,
  })
}
