import { ref } from 'vue'

/**
 * 通用流式聊天处理 composable
 * 封装 SSE 流式读取、错误处理和内容累积逻辑
 */
export function useStreamingChat() {
  const accumulatedContent = ref('')
  const isStreaming = ref(false)
  const hasError = ref(false)

  /**
   * 开始流式处理
   * @param {ReadableStreamDefaultReader} reader - fetch 返回的 reader
   * @param {Object} options - 配置选项
   * @param {Function} options.onChunk - 每收到一个 chunk 时的回调，参数为累积内容
   * @param {Function} options.onComplete - 流完成时的回调
   * @param {Function} options.onError - 发生错误时的回调
   * @param {Function} options.updateMessage - 更新消息的函数，参数为 assistantMessage 和 lastIndex
   * @param {Array} options.messagesRef - 消息数组的 ref
   * @param {Function} options.scrollToBottom - 滚动到底部的函数
   * @returns {Promise<void>}
   */
  async function startStreaming(reader, options = {}) {
    const {
      onChunk,
      onComplete,
      onError,
      updateMessage,
      messagesRef,
      scrollToBottom
    } = options

    const decoder = new TextDecoder('utf-8')
    accumulatedContent.value = ''
    hasError.value = false
    isStreaming.value = true

    try {
      while (true) {
        try {
          const { value, done } = await reader.read()
          if (done) break

          const newContent = decoder.decode(value)

          // 检查是否是错误消息
          if (newContent.includes('"type":"error"')) {
            hasError.value = true
            try {
              const errorMatch = newContent.match(/"content":"([^"]+)"/)
              if (errorMatch && errorMatch[1]) {
                accumulatedContent.value = errorMatch[1]
                if (onError) {
                  onError(errorMatch[1])
                }
              }
            } catch (e) {
              accumulatedContent.value = '服务暂时不可用，请稍后重试'
              if (onError) {
                onError('服务暂时不可用，请稍后重试')
              }
            }
            break
          }

          accumulatedContent.value += newContent

          if (onChunk) {
            onChunk(accumulatedContent.value)
          }

          // 如果提供了 updateMessage 和 messagesRef，自动更新最后一条消息
          if (updateMessage && messagesRef) {
            const lastIndex = messagesRef.value.length - 1
            if (lastIndex >= 0) {
              const assistantMessage = messagesRef.value[lastIndex]
              assistantMessage.content = accumulatedContent.value
              updateMessage(assistantMessage, lastIndex)
            }
          }

          if (scrollToBottom) {
            await scrollToBottom()
          }
        } catch (readError) {
          console.error('读取流错误:', readError)
          if (onError) {
            onError(readError)
          }
          break
        }
      }

      // 如果有错误，标记消息为错误类型
      if (hasError.value && messagesRef) {
        const lastIndex = messagesRef.value.length - 1
        if (lastIndex >= 0) {
          const assistantMessage = messagesRef.value[lastIndex]
          assistantMessage.role = 'error'
          assistantMessage.type = 'error'
        }
      }

      if (onComplete) {
        onComplete()
      }
    } finally {
      isStreaming.value = false
    }
  }

  /**
   * 解析 SSE 事件
   * @param {String} buffer - 输入缓冲区
   * @param {Function} onSessionCreated - 遇到 session-created 事件时的回调
   * @returns {Object} { content, sessionCreated }
   */
  function parseSSEvent(buffer, onSessionCreated) {
    let sessionCreated = null

    if (buffer.includes('event: session-created')) {
      const lines = buffer.split('\n')

      for (let i = 0; i < lines.length; i++) {
        if (lines[i].startsWith('event: session-created')) {
          if (i + 1 < lines.length && lines[i + 1].startsWith('data: ')) {
            const dataStr = lines[i + 1].substring(6)
            try {
              const eventData = JSON.parse(dataStr)
              if (onSessionCreated && eventData.sessionId) {
                sessionCreated = eventData.sessionId
                onSessionCreated(eventData.sessionId)
              }
            } catch (e) {
              console.error('解析 session-created 事件失败:', e)
            }
          }
          // 返回移除事件后的内容
          return {
            content: lines.slice(i + 2).join('\n'),
            sessionCreated
          }
        }
      }
    }

    return { content: buffer, sessionCreated }
  }

  return {
    accumulatedContent,
    isStreaming,
    hasError,
    startStreaming,
    parseSSEvent
  }
}
