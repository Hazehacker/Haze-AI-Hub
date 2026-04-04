<template>
  <div class="astra-chat">
    <!-- 左侧会话列表 -->
    <aside class="sidebar">
      <div class="sidebar-header">
        <button class="new-chat-btn" @click="startNewChat">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/>
            <line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          新建会话
        </button>
      </div>
      <div class="session-list">
        <div
          v-for="session in sessions"
          :key="session.id"
          :class="['session-item', { active: session.id === currentSessionId }]"
          @click="selectSession(session)"
        >
          <div class="session-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>
            </svg>
          </div>
          <div class="session-info">
            <div class="session-title">{{ session.title || '新对话' }}</div>
            <div class="session-time">{{ formatTime(session.lastActiveAt) }}</div>
          </div>
        </div>
      </div>
    </aside>

    <!-- 右侧聊天区域 -->
    <main class="chat-area">
      <!-- 聊天头部 -->
      <header class="chat-header">
        <div class="chat-info">
          <h2 class="library-name">{{ library?.name }}</h2>
          <div class="chat-stats">
            <span>{{ library?.mediaCount || 0 }} 文档</span>
            <span>{{ library?.chunkCount || 0 }} 分片</span>
          </div>
        </div>
        <button class="back-btn" @click="goBack">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
        </button>
      </header>

      <!-- 欢迎页 -->
      <div class="welcome-page" v-if="messages.length === 0 && !isLoading">
        <div class="welcome-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
            <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
          </svg>
        </div>
        <h1>欢迎使用知识库问答</h1>
        <p>基于 RAG 检索增强生成，智能回答您的问题</p>
        <div class="suggestions">
          <div
            v-for="(question, index) in suggestions"
            :key="index"
            class="suggestion-card"
            @click="sendQuestion(question)"
          >
            <span>{{ question }}</span>
          </div>
        </div>
      </div>

      <!-- 消息列表 -->
      <div class="message-list" ref="messageList" v-else>
        <div v-for="(msg, index) in messages" :key="index" :class="['message', msg.role]">
          <div class="message-avatar">
            <svg v-if="msg.role === 'user'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
              <circle cx="12" cy="7" r="4"/>
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2a10 10 0 1 0 10 10H12V2z"/>
              <path d="M12 2a10 10 0 0 1 10 10"/>
              <circle cx="12" cy="12" r="6"/>
            </svg>
          </div>
          <div class="message-content">
            <div class="message-text" v-html="formatMessage(msg.content)"></div>
            <div class="message-references" v-if="msg.references && msg.references.length">
              <div class="references-header">参考文档:</div>
              <div
                v-for="(ref, idx) in msg.references"
                :key="idx"
                class="reference-item"
              >
                {{ ref }}
              </div>
            </div>
          </div>
        </div>

        <!-- 加载中 -->
        <div class="message assistant loading" v-if="isLoading">
          <div class="message-avatar">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2a10 10 0 1 0 10 10H12V2z"/>
            </svg>
          </div>
          <div class="message-content">
            <div class="typing-indicator">
              <span></span>
              <span></span>
              <span></span>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="input-area">
        <div class="input-wrapper">
          <textarea
            v-model="inputText"
            placeholder="输入您的问题..."
            rows="1"
            @keydown.enter.exact.prevent="sendMessage"
          ></textarea>
          <button class="send-btn" @click="sendMessage" :disabled="!inputText.trim() || isLoading">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="22" y1="2" x2="11" y2="13"/>
              <polygon points="22 2 15 22 11 13 2 9 22 2"/>
            </svg>
          </button>
        </div>
        <div class="input-hint">
          <span>基于 {{ library?.name }} 回答</span>
          <span>使用 RAG + ReRank</span>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { astraAPI, chatAPI } from '@/services/api'

const router = useRouter()
const route = useRoute()

const libraryId = route.params.libraryId
const library = ref(null)
const sessions = ref([])
const currentSessionId = ref(null)
const messages = ref([])
const inputText = ref('')
const isLoading = ref(false)
const messageList = ref(null)

const suggestions = [
  '这个知识库的主要内容是什么？',
  '如何开始使用这个知识库？',
  '知识库支持哪些文件格式？',
  '如何上传和管理文档？'
]

async function loadLibrary() {
  library.value = await astraAPI.getLibrary(libraryId)
}

async function loadSessions() {
  const data = await chatAPI.getChatHistory('astra')
  sessions.value = data.filter(s => {
    // 简单过滤，实际上需要根据 knowledgeLibraryId 过滤
    return true
  })
}

function startNewChat() {
  currentSessionId.value = null
  messages.value = []
}

async function selectSession(session) {
  currentSessionId.value = session.id
  const history = await astraAPI.getSessionMessages(session.id)
  messages.value = history.map(msg => ({
    role: msg.role === 'U' ? 'user' : 'assistant',
    content: msg.content,
    references: msg.metadataJson?.references || []
  }))
}

async function sendMessage() {
  if (!inputText.value.trim() || isLoading.value) return
  const question = inputText.value.trim()
  inputText.value = ''

  // 添加用户消息
  messages.value.push({
    role: 'user',
    content: question
  })

  await nextTick()
  scrollToBottom()

  isLoading.value = true

  try {
    const reader = await astraAPI.chat(libraryId, question, currentSessionId.value)
    let assistantMessage = ''
    const decoder = new TextDecoder('utf-8')

    // 处理 SSE 流
    while (true) {
      const { value, done } = await reader.read()
      if (done) break

      const chunk = decoder.decode(value, { stream: true })
      assistantMessage += chunk

      // 更新消息
      const lastMsg = messages.value[messages.value.length - 1]
      if (lastMsg?.role === 'user') {
        messages.value.push({
          role: 'assistant',
          content: assistantMessage
        })
      } else {
        lastMsg.content = assistantMessage
      }

      await nextTick()
      scrollToBottom()
    }

    // 如果没有收到回复，显示提示
    if (!assistantMessage) {
      messages.value.push({
        role: 'assistant',
        content: '抱歉，暂时无法回答您的问题，请稍后重试。'
      })
    }
  } catch (error) {
    console.error('发送消息失败:', error)
    messages.value.push({
      role: 'assistant',
      content: '抱歉，发生了错误: ' + error.message
    })
  } finally {
    isLoading.value = false
    await nextTick()
    scrollToBottom()
  }
}

function sendQuestion(question) {
  inputText.value = question
  sendMessage()
}

function goBack() {
  router.push(`/astra/library/${libraryId}`)
}

function formatMessage(content) {
  if (!content) return ''
  // 简单的 Markdown 格式化
  return content
    .replace(/\n/g, '<br>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
}

function formatTime(timestamp) {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  const now = new Date()
  const diff = now - date

  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`

  return date.toLocaleDateString('zh-CN')
}

function scrollToBottom() {
  if (messageList.value) {
    messageList.value.scrollTop = messageList.value.scrollHeight
  }
}

onMounted(() => {
  loadLibrary()
  loadSessions()
})
</script>

<style scoped>
.astra-chat {
  display: flex;
  height: 100vh;
}

.sidebar {
  width: 280px;
  background: #f8f9fa;
  border-right: 1px solid #e0e0e0;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 16px;
}

.new-chat-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.3);
}

.new-chat-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
}

.new-chat-btn svg {
  width: 18px;
  height: 18px;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.session-item:hover {
  background: rgba(0, 0, 0, 0.05);
}

.session-item.active {
  background: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.session-icon {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.session-icon svg {
  width: 18px;
  height: 18px;
}

.session-title {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin-bottom: 2px;
}

.session-time {
  font-size: 12px;
  color: #999;
}

.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: white;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  border-bottom: 1px solid #e0e0e0;
}

.library-name {
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.chat-stats {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #999;
  margin-top: 4px;
}

.back-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  border: none;
  border-radius: 8px;
  cursor: pointer;
}

.back-btn:hover {
  background: #e0e0e0;
}

.back-btn svg {
  width: 20px;
  height: 20px;
}

.welcome-page {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.welcome-icon {
  width: 80px;
  height: 80px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  margin-bottom: 24px;
  box-shadow: 0 8px 30px rgba(102, 126, 234, 0.3);
}

.welcome-icon svg {
  width: 48px;
  height: 48px;
}

.welcome-page h1 {
  font-size: 24px;
  color: #333;
  margin-bottom: 8px;
}

.welcome-page p {
  font-size: 14px;
  color: #999;
  margin-bottom: 32px;
}

.suggestions {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  max-width: 600px;
  width: 100%;
}

.suggestion-card {
  padding: 16px;
  background: #f8f9fa;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  text-align: left;
}

.suggestion-card:hover {
  background: #f0f0f0;
  transform: translateY(-2px);
}

.suggestion-card span {
  font-size: 14px;
  color: #333;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.message {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
}

.message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.message.user .message-avatar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.message.assistant .message-avatar {
  background: linear-gradient(135deg, #764ba2 0%, #667eea 100%);
  color: white;
}

.message-avatar svg {
  width: 18px;
  height: 18px;
}

.message-content {
  max-width: 70%;
}

.message.user .message-content {
  text-align: right;
}

.message-text {
  display: inline-block;
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
}

.message.user .message-text {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.message.assistant .message-text {
  background: #f5f5f5;
  color: #333;
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 16px;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  background: #999;
  border-radius: 50%;
  animation: typing 1.4s infinite;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-8px); }
}

.input-area {
  padding: 16px 24px;
  border-top: 1px solid #e0e0e0;
}

.input-wrapper {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.input-wrapper textarea {
  flex: 1;
  padding: 12px 16px;
  border: 1px solid #e0e0e0;
  border-radius: 12px;
  font-size: 14px;
  resize: none;
  outline: none;
  font-family: inherit;
}

.input-wrapper textarea:focus {
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.send-btn {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.3);
}

.send-btn:hover:not(:disabled) {
  transform: scale(1.05);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.send-btn svg {
  width: 20px;
  height: 20px;
}

.input-hint {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin-top: 12px;
  font-size: 12px;
  color: #999;
}
</style>
