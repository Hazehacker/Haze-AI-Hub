import request from '@/utils/request'
import { getToken } from '@/utils/auth'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const BASE_URL = 'http://localhost:8080/api/v1'

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

  // 发送聊天消息（带思考过程）- 支持 SSE 事件
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
        // FormData 会自动设置 Content-Type
        // 将 sessionId 添加到 FormData 中（首条消息时为 null）
        if (sessionId) {
          data.append('sessionId', sessionId)
        }
        // 默认启用思考过程
        if (!data.has('enableThinking')) {
          data.append('enableThinking', 'true')
        }
        // 设置思考预算（可选）
        if (!data.has('thinkingBudget')) {
          data.append('thinkingBudget', '10000')
        }
        body = data
      } else {
        headers['Content-Type'] = 'application/x-www-form-urlencoded'
        const params = new URLSearchParams({ 
          prompt: data,
          enableThinking: 'true',
          thinkingBudget: '10000'
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
        // 处理 401 未授权错误
        if (response.status === 401) {
          ElMessage.error('请先登录')
          throw new Error('请先登录')
        }
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      // 返回一个包装的 reader，可以处理 SSE 事件
      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''
      
      return {
        async read() {
          const { value, done } = await reader.read()
          if (done) return { value: undefined, done: true }
          
          const chunk = decoder.decode(value, { stream: true })
          buffer += chunk
          
          // 检查是否包含 SSE 事件
          if (buffer.includes('event: session-created')) {
            const lines = buffer.split('\n')
            let eventData = null
            
            for (let i = 0; i < lines.length; i++) {
              if (lines[i].startsWith('event: session-created')) {
                // 下一行应该是 data
                if (i + 1 < lines.length && lines[i + 1].startsWith('data: ')) {
                  const dataStr = lines[i + 1].substring(6) // 移除 "data: "
                  try {
                    eventData = JSON.parse(dataStr)
                    if (onSessionCreated && eventData.sessionId) {
                      onSessionCreated(eventData.sessionId)
                    }
                  } catch (e) {
                    console.error('解析 session-created 事件失败:', e)
                  }
                }
                // 移除已处理的事件
                buffer = lines.slice(i + 2).join('\n')
                break
              }
            }
          }
          
          // 返回非事件的内容
          const content = buffer.replace(/event: .*\ndata: .*\n\n/g, '')
          buffer = ''
          
          return { 
            value: new TextEncoder().encode(content), 
            done: false 
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