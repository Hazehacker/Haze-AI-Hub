import request from '@/utils/request'
import { getToken } from '@/utils/auth'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const BASE_URL = 'http://localhost:8080/api/v1'

/**
 * 解析单个 SSE 事件块（后端返回 Flux<String>，Spring 以纯文本 data: 格式输出，无 JSON 编码）
 *
 * 内容约定：
 *   SESSION_CREATED:<id>   新会话创建通知
 *   ERROR:<message>        错误通知
 *   <think>...</think>     思考内容片段
 *   其他文本               回答内容片段
 *
 * @param {string} eventBlock - 一个完整的 SSE 事件（由 \n\n 分隔）
 * @param {Function} onSessionCreated - 新会话创建回调 (sessionId: string) => void
 * @returns {{ content?: string, error?: string }}
 */
function parseSSEEvent(eventBlock, onSessionCreated) {
  const lines = eventBlock.split('\n')
  const dataLines = []

  for (const line of lines) {
    if (line.startsWith('data:')) {
      dataLines.push(line.substring(5))
    }
    // 忽略 event:, id:, retry:, 注释行 (:)
  }

  if (dataLines.length === 0) return {} // 空事件（心跳等）

  // 多行 data 拼回原始内容（Spring SSE 会把换行内容拆成多个 data: 行）
  const data = dataLines.join('\n')

  // 会话创建通知
  if (data.startsWith('SESSION_CREATED:')) {
    const sessionId = data.substring('SESSION_CREATED:'.length).trim()
    if (onSessionCreated) {
      onSessionCreated(sessionId)
    }
    return {}
  }

  // 错误通知
  if (data.startsWith('ERROR:')) {
    return { error: data.substring('ERROR:'.length) }
  }

  // 默认：内容 chunk（<think>...</think> 或回答文本）
  return { content: data }
}

export const chatAPI = {
  // 创建新会话
  async createSession(userId, type, title = null) {
    try {
      const token = getToken()
      const params = new URLSearchParams({
        userId: userId.toString(),
        type: type
      })
      if (title) {
        params.append('title', title)
      }
      
      const response = await fetch(`${BASE_URL}/ai/session/create?${params}`, {
        method: 'POST',
        headers: {
          'authentication': token || ''
        }
      })
      
      if (!response.ok) {
        if (response.status === 401) {
          ElMessage.error('请先登录')
          throw new Error('请先登录')
        }
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      
      const result = await response.json()
      if (result.code == 200) {
        return result.data  // 返回会话ID
      } else {
        throw new Error(result.msg || '创建会话失败')
      }
    } catch (error) {
      console.error('创建会话失败:', error)
      throw error
    }
  },

  // 发送聊天消息（带思考过程）- 解析 SSE 事件流
  async sendMessage(data, sessionId, onSessionCreated) {
    try {
      const url = `${BASE_URL}/ai/text-chat`
      
      const token = getToken()
      const headers = {
        'authentication': token || ''
      }
      
      // 根据数据类型设置不同的请求体
      let body
      if (data instanceof FormData) {
        if (sessionId) {
          data.append('sessionId', sessionId)
        }
        if (!data.has('enableThinking')) {
          data.append('enableThinking', 'true')
        }
        body = data
      } else {
        headers['Content-Type'] = 'application/x-www-form-urlencoded'
        const params = new URLSearchParams({ 
          prompt: data,
          enableThinking: 'true'
        })
        if (sessionId) {
          params.append('sessionId', sessionId)
        }
        body = params
      }
      
      const response = await fetch(url, {
        method: 'POST',
        headers: headers,
        body: body
      })

      if (!response.ok) {
        if (response.status === 401) {
          ElMessage.error('请先登录')
          throw new Error('请先登录')
        }
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      // 返回 SSE 解析包装的 reader，自动处理 data: 前缀和命名事件
      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      return {
        async read() {
          while (true) {
            const { value, done } = await reader.read()

            if (done) {
              // 处理缓冲区中剩余的内容
              if (buffer.trim()) {
                const result = parseSSEEvent(buffer, onSessionCreated)
                buffer = ''
                if (result.error) {
                  throw new Error(result.error)
                }
                if (result.content) {
                  return { value: new TextEncoder().encode(result.content), done: false }
                }
              }
              return { value: undefined, done: true }
            }

            buffer += decoder.decode(value, { stream: false })

            // 按 \n\n 分隔解析完整的 SSE 事件
            // 关键：每收到一个完整的 SSE 事件就立即返回，实现真正的流式
            while (buffer.includes('\n\n')) {
              const idx = buffer.indexOf('\n\n')
              const eventBlock = buffer.substring(0, idx)
              buffer = buffer.substring(idx + 2)

              const parsed = parseSSEEvent(eventBlock, onSessionCreated)
              if (parsed.error) {
                throw new Error(parsed.error)
              }
              if (parsed.content) {
                return { value: new TextEncoder().encode(parsed.content), done: false }
              }
            }
            // 尚无完整事件，继续读取
          }
        }
      }
    } catch (error) {
      console.error('API Error:', error)
      throw error
    }
  },

  // 获取会话列表（使用新接口）
  async getChatHistory(type = 'chat') {
    try {
      const token = getToken()
      const response = await fetch(`${BASE_URL}/ai/session/list?type=${type}`, {
        headers: {
          'authentication': token || ''
        }
      })
      if (!response.ok) {
        if (response.status === 401) {
          const userStore = useUserStore()
          ElMessage.error('请先登录')
          userStore.logout()
          setTimeout(() => {
            window.dispatchEvent(new CustomEvent('open-login-dialog'))
          }, 100)
          return []
        }
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      const result = await response.json()
      
      // 处理返回的Result对象
      if (result.code == 200 && result.data) {
        return result.data.map(session => ({
          id: session.id,
          title: session.title || '新对话',
          type: session.type,
          lastActiveAt: session.lastActiveAt,
          createdAt: session.createdAt,
          isPinned: session.isPinned
        }))
      }
      
      return []
    } catch (error) {
      console.error('获取会话列表失败:', error)
      return []
    }
  },

  // 获取特定对话的消息历史
  async getChatMessages(sessionId, type = 'chat') {  // 添加类型参数
    try {
      const token = getToken()
      const response = await fetch(`${BASE_URL}/ai/history/${type}/${sessionId}`, {
        headers: {
          'authentication': token || ''
        }
      })
      if (!response.ok) {
        if (response.status === 401) {
          const userStore = useUserStore()
          ElMessage.error('请先登录')
          userStore.logout()
          setTimeout(() => {
            window.dispatchEvent(new CustomEvent('open-login-dialog'))
          }, 100)
          return []
        }
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      const result = await response.json()
      
      // 处理返回的Result对象
      if (result.code == 200 && result.data) {
        // 添加时间戳并转换为前端需要的格式
        return result.data.map(msg => ({
          role: msg.role,
          content: msg.content,
          timestamp: msg.createdAt ? new Date(msg.createdAt) : new Date(),
          metadata: msg.metadataJson // 保留元数据
        }))
      }
      
      return []
    } catch (error) {
      console.error('API Error:', error)
      return []
    }
  },

  // 发送游戏消息
  async sendGameMessage(prompt, sessionId) {
    try {
      const token = getToken()
      const params = new URLSearchParams({
        prompt: prompt,
        sessionId: sessionId
      })
      
      const response = await fetch(`${BASE_URL}/ai/game`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'authentication': token || ''
        },
        body: params
      })

      if (!response.ok) {
        if (response.status === 401) {
          ElMessage.error('请先登录')
          throw new Error('请先登录')
        }
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      return response.body.getReader()
    } catch (error) {
      console.error('API Error:', error)
      throw error
    }
  },

  // 发送客服消息
  async sendServiceMessage(prompt, sessionId) {
    try {
      const token = getToken()
      const response = await fetch(`${BASE_URL}/ai/service?prompt=${encodeURIComponent(prompt)}&sessionId=${sessionId}`, {
        method: 'GET',
        headers: {
          'authentication': token || ''
        }
      })

      if (!response.ok) {
        if (response.status === 401) {
          ElMessage.error('请先登录')
          throw new Error('请先登录')
        }
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      return response.body.getReader()
    } catch (error) {
      console.error('API Error:', error)
      throw error
    }
  },

  // 发送 PDF 问答消息
  async sendPdfMessage(prompt, sessionId) {
    try {
      const token = getToken()
      const response = await fetch(`${BASE_URL}/ai/pdf/chat?prompt=${encodeURIComponent(prompt)}&sessionId=${sessionId}`, {
        method: 'GET',
        headers: {
          'authentication': token || ''
        },
        // 确保使用流式响应
        signal: AbortSignal.timeout(30000) // 30秒超时
      })

      if (!response.ok) {
        if (response.status === 401) {
          ElMessage.error('请先登录')
          throw new Error('请先登录')
        }
        throw new Error(`API error: ${response.status}`)
      }

      // 返回可读流
      return response.body.getReader()
    } catch (error) {
      console.error('API Error:', error)
      throw error
    }
  },

  // 获取可用模型列表
  async getModelList() {
    try {
      const token = getToken()
      const response = await fetch(`${BASE_URL}/ai/models`, {
        method: 'GET',
        headers: {
          'authentication': token || ''
        }
      })

      if (!response.ok) {
        if (response.status === 401) {
          const userStore = useUserStore()
          ElMessage.error('请先登录')
          userStore.logout()
          setTimeout(() => {
            window.dispatchEvent(new CustomEvent('open-login-dialog'))
          }, 100)
          return []
        }
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      const result = await response.json()
      if (result.code == 200 && result.data) {
        // 转换字段名以适配前端
        return result.data.map(model => ({
          id: model.id,
          name: model.name,
          value: model.name, // 使用name作为API调用值
          description: model.description,
          recommended: model.isRecommended,
          beta: model.isBeta,
          sort: model.sort,
          status: model.status
        }))
      }

      return []
    } catch (error) {
      console.error('获取模型列表失败:', error)
      return []
    }
  }
}

// Astra 知识库 API
export const astraAPI = {
  // 创建知识库
  async createLibrary(data) {
    try {
      const token = getToken()
      const response = await fetch(`${BASE_URL}/astra/libraries`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'authentication': token || ''
        },
        body: JSON.stringify(data)
      })
      const result = await response.json()
      if (result.code == 200) {
        return result.data
      } else {
        throw new Error(result.msg || '创建知识库失败')
      }
    } catch (error) {
      console.error('创建知识库失败:', error)
      throw error
    }
  },

  // 获取知识库列表
  async getLibraries(keyword = '', page = 0, size = 20) {
    try {
      const token = getToken()
      const params = new URLSearchParams({ page, size })
      if (keyword) params.append('keyword', keyword)
      const response = await fetch(`${BASE_URL}/astra/libraries?${params}`, {
        headers: { 'authentication': token || '' }
      })
      const result = await response.json()
      if (result.code == 200) {
        return result.data || []
      }
      return []
    } catch (error) {
      console.error('获取知识库列表失败:', error)
      return []
    }
  },

  // 获取知识库详情
  async getLibrary(id) {
    try {
      const token = getToken()
      const response = await fetch(`${BASE_URL}/astra/libraries/${id}`, {
        headers: { 'authentication': token || '' }
      })
      const result = await response.json()
      if (result.code == 200) {
        return result.data
      }
      return null
    } catch (error) {
      console.error('获取知识库详情失败:', error)
      return null
    }
  },

  // 更新知识库
  async updateLibrary(id, data) {
    try {
      const token = getToken()
      const response = await fetch(`${BASE_URL}/astra/libraries/${id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'authentication': token || ''
        },
        body: JSON.stringify(data)
      })
      const result = await response.json()
      if (result.code == 200) {
        return result.data
      }
      return null
    } catch (error) {
      console.error('更新知识库失败:', error)
      throw error
    }
  },

  // 删除知识库
  async deleteLibrary(id) {
    try {
      const token = getToken()
      const response = await fetch(`${BASE_URL}/astra/libraries/${id}`, {
        method: 'DELETE',
        headers: { 'authentication': token || '' }
      })
      const result = await response.json()
      if (result.code == 200) {
        return true
      }
      return false
    } catch (error) {
      console.error('删除知识库失败:', error)
      return false
    }
  },

  // 置顶/取消置顶知识库
  async toggleTop(id) {
    try {
      const token = getToken()
      const response = await fetch(`${BASE_URL}/astra/libraries/${id}/toggle-top`, {
        method: 'PUT',
        headers: { 'authentication': token || '' }
      })
      const result = await response.json()
      if (result.code == 200) {
        return result.data
      }
      return null
    } catch (error) {
      console.error('切换置顶状态失败:', error)
      return null
    }
  },

  // 上传文件
  async uploadFile(file, libraryId, onProgress) {
    try {
      const token = getToken()
      const formData = new FormData()
      formData.append('file', file)
      formData.append('libraryId', libraryId)

      const response = await fetch(`${BASE_URL}/astra/media`, {
        method: 'POST',
        headers: { 'authentication': token || '' },
        body: formData
      })

      const result = await response.json()
      if (result.code == 200) {
        return result.data
      } else {
        throw new Error(result.msg || '上传文件失败')
      }
    } catch (error) {
      console.error('上传文件失败:', error)
      throw error
    }
  },

  // 获取知识库下的文件列表
  async getMediaList(libraryId, status = '', page = 0, size = 20) {
    try {
      const token = getToken()
      const params = new URLSearchParams({ page, size })
      if (status) params.append('status', status)
      const response = await fetch(`${BASE_URL}/astra/libraries/${libraryId}/media?${params}`, {
        headers: { 'authentication': token || '' }
      })
      const result = await response.json()
      if (result.code == 200) {
        return result.data || []
      }
      return []
    } catch (error) {
      console.error('获取文件列表失败:', error)
      return []
    }
  },

  // 获取文件详情
  async getMedia(id) {
    try {
      const token = getToken()
      const response = await fetch(`${BASE_URL}/astra/media/${id}`, {
        headers: { 'authentication': token || '' }
      })
      const result = await response.json()
      if (result.code == 200) {
        return result.data
      }
      return null
    } catch (error) {
      console.error('获取文件详情失败:', error)
      return null
    }
  },

  // 获取文件解析状态
  async getMediaStatus(id) {
    try {
      const token = getToken()
      const response = await fetch(`${BASE_URL}/astra/media/${id}/status`, {
        headers: { 'authentication': token || '' }
      })
      const result = await response.json()
      if (result.code == 200) {
        return result.data
      }
      return null
    } catch (error) {
      console.error('获取文件状态失败:', error)
      return null
    }
  },

  // 删除文件
  async deleteMedia(id) {
    try {
      const token = getToken()
      const response = await fetch(`${BASE_URL}/astra/media/${id}`, {
        method: 'DELETE',
        headers: { 'authentication': token || '' }
      })
      const result = await response.json()
      if (result.code == 200) {
        return true
      }
      return false
    } catch (error) {
      console.error('删除文件失败:', error)
      return false
    }
  },

  // 订阅解析进度 SSE
  subscribeParseProgress(mediaId, callbacks) {
    const token = getToken()
    const eventSource = new EventSource(`${BASE_URL}/astra/media/${mediaId}/stream`, {
      headers: { 'authentication': token || '' }
    })

    eventSource.addEventListener('progress', (e) => {
      if (callbacks.onProgress) {
        callbacks.onProgress(JSON.parse(e.data))
      }
    })

    eventSource.addEventListener('complete', (e) => {
      if (callbacks.onComplete) {
        callbacks.onComplete(JSON.parse(e.data))
      }
      eventSource.close()
    })

    eventSource.addEventListener('error', (e) => {
      if (callbacks.onError) {
        callbacks.onError(e)
      }
      eventSource.close()
    })

    return eventSource
  },

  // 知识问答
  async chat(libraryId, prompt, sessionId = null) {
    try {
      const token = getToken()
      const response = await fetch(`${BASE_URL}/astra/chat`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'authentication': token || ''
        },
        body: JSON.stringify({ libraryId, prompt, sessionId })
      })

      if (!response.ok) {
        if (response.status === 401) {
          ElMessage.error('请先登录')
          throw new Error('请先登录')
        }
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      return response.body.getReader()
    } catch (error) {
      console.error('知识问答失败:', error)
      throw error
    }
  },

  // 获取会话历史消息
  async getSessionMessages(sessionId) {
    try {
      const token = getToken()
      const response = await fetch(`${BASE_URL}/astra/sessions/${sessionId}/messages`, {
        headers: { 'authentication': token || '' }
      })
      
      if (!response.ok) {
        if (response.status === 401) {
          const userStore = useUserStore()
          ElMessage.error('请先登录')
          userStore.logout()
          setTimeout(() => {
            window.dispatchEvent(new CustomEvent('open-login-dialog'))
          }, 100)
          return []
        }
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      
      const result = await response.json()
      if (result.code == 200) {
        return result.data || []
      }
      return []
    } catch (error) {
      console.error('获取会话消息失败:', error)
      return []
    }
  }
}