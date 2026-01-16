import request from '@/utils/request'
import { getToken } from '@/utils/auth'

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
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      
      const result = await response.json()
      if (result.code === 200) {
        return result.data  // 返回会话ID
      } else {
        throw new Error(result.msg || '创建会话失败')
      }
    } catch (error) {
      console.error('创建会话失败:', error)
      throw error
    }
  },

  // 发送聊天消息
  async sendMessage(data, chatId) {
    try {
      let url = `${BASE_URL}/ai/chat-with-thinking-text`
      if (chatId) {
        url += `?chatId=${chatId}`
      }
      
      const token = getToken()
      const headers = {
        'authentication': token || ''
      }
      
      // 根据数据类型设置不同的请求体
      if (data instanceof FormData) {
        // FormData 会自动设置 Content-Type
      } else {
        headers['Content-Type'] = 'application/x-www-form-urlencoded'
      }
      
      const response = await fetch(url, {
        method: 'POST',
        headers: headers,
        body: data instanceof FormData ? data : 
          new URLSearchParams({ prompt: data })
      })

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      return response.body.getReader()
    } catch (error) {
      console.error('API Error:', error)
      throw error
    }
  },

  // 获取聊天历史列表
  async getChatHistory(type = 'chat') {  // 添加类型参数
    try {
      const token = getToken()
      const response = await fetch(`${BASE_URL}/ai/history/${type}`, {
        headers: {
          'authentication': token || ''
        }
      })
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      const chatIds = await response.json()
      // 转换为前端需要的格式
      return chatIds.map(id => ({
        id,
        title: type === 'pdf' ? `PDF对话 ${id.slice(-6)}` : 
               type === 'service' ? `咨询 ${id.slice(-6)}` :
               `对话 ${id.slice(-6)}`
      }))
    } catch (error) {
      console.error('API Error:', error)
      return []
    }
  },

  // 获取特定对话的消息历史
  async getChatMessages(chatId, type = 'chat') {  // 添加类型参数
    try {
      const token = getToken()
      const response = await fetch(`${BASE_URL}/ai/history/${type}/${chatId}`, {
        headers: {
          'authentication': token || ''
        }
      })
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      const messages = await response.json()
      // 添加时间戳
      return messages.map(msg => ({
        ...msg,
        timestamp: new Date() // 由于后端没有提供时间戳，这里临时使用当前时间
      }))
    } catch (error) {
      console.error('API Error:', error)
      return []
    }
  },

  // 发送游戏消息
  async sendGameMessage(prompt, chatId) {
    try {
      const token = getToken()
      const response = await fetch(`${BASE_URL}/ai/game?prompt=${encodeURIComponent(prompt)}&chatId=${chatId}`, {
        method: 'GET',
        headers: {
          'authentication': token || ''
        }
      })

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      return response.body.getReader()
    } catch (error) {
      console.error('API Error:', error)
      throw error
    }
  },

  // 发送客服消息
  async sendServiceMessage(prompt, chatId) {
    try {
      const token = getToken()
      const response = await fetch(`${BASE_URL}/ai/service?prompt=${encodeURIComponent(prompt)}&chatId=${chatId}`, {
        method: 'GET',
        headers: {
          'authentication': token || ''
        }
      })

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      return response.body.getReader()
    } catch (error) {
      console.error('API Error:', error)
      throw error
    }
  },

  // 发送 PDF 问答消息
  async sendPdfMessage(prompt, chatId) {
    try {
      const token = getToken()
      const response = await fetch(`${BASE_URL}/ai/pdf/chat?prompt=${encodeURIComponent(prompt)}&chatId=${chatId}`, {
        method: 'GET',
        headers: {
          'authentication': token || ''
        },
        // 确保使用流式响应
        signal: AbortSignal.timeout(30000) // 30秒超时
      })

      if (!response.ok) {
        throw new Error(`API error: ${response.status}`)
      }

      // 返回可读流
      return response.body.getReader()
    } catch (error) {
      console.error('API Error:', error)
      throw error
    }
  }
} 