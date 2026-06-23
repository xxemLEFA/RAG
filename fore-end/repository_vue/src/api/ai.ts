export interface AiChatResponse {
  answer: string
}

export interface RagSourceItem {
  fileName: string
  snippet: string
  score: number | null
}

export interface AiRagResponse {
  answer: string
  sources: RagSourceItem[]
  knowledgeHit: boolean
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
