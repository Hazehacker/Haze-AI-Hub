<template>
  <div class="ai-chat" :class="{ 'dark': isDark }">
    <div class="chat-container">
      <div class="sidebar">
        <div class="history-header">
          <h2>聊天记录</h2>
          <button class="new-chat" @click="startNewChat">
            <PlusIcon class="icon" />
            新对话
          </button>
        </div>
        
        <div class="group-section">
          <div class="group-header">
            <span class="group-title">分组</span>
            <button class="add-group-btn" @click="showGroupDialog = true" title="创建分组">
              <PlusIcon class="icon" />
            </button>
          </div>
          
          <div class="group-list">
            <div 
              v-for="group in groups" 
              :key="group.id"
              class="group-item"
              :class="{ 'active': selectedGroupId === group.id }"
              @click="selectGroup(group.id)"
            >
              <span class="group-icon">📁</span>
              <span class="group-name">{{ group.name }}</span>
              <div class="group-actions">
                <button class="action-btn" @click.stop="editGroup(group)" title="编辑">
                  <PencilIcon class="icon" />
                </button>
                <button class="action-btn delete" @click.stop="deleteGroupConfirm(group.id)" title="删除">
                  <TrashIcon class="icon" />
                </button>
              </div>
            </div>
          </div>
        </div>
        
        <div class="history-list">
          <div 
            v-for="chat in filteredChatHistory" 
            :key="chat.id"
            class="history-item"
            :class="{ 'active': currentChatId === chat.id }"
            @click="loadChat(chat.id)"
          >
            <ChatBubbleLeftRightIcon class="icon" />
            <span class="title">{{ chat.title || '新对话' }}</span>
          </div>
        </div>
      </div>
      
      <div class="chat-main">
        <!-- 模型选择器 -->
        <div v-if="selectedModel" class="model-selector">
          <div class="model-dropdown" @click="toggleModelDropdown">
            <span class="model-name">{{ selectedModel.name }}</span>
            <ChevronDownIcon class="icon" :class="{ 'rotate': showModelDropdown }" />
          </div>
          
          <transition name="dropdown">
            <div v-if="showModelDropdown" class="model-list">
              <div class="model-category">
                <div class="category-title">模型</div>
                <div 
                  v-for="model in availableModels" 
                  :key="model.id"
                  class="model-item"
                  :class="{ 'active': selectedModel.id === model.id, 'recommended': model.recommended }"
                  @click="selectModel(model)"
                >
                  <div class="model-info">
                    <div class="model-header">
                      <span class="model-title">{{ model.name }}</span>
                      <span v-if="model.recommended" class="badge">推荐</span>
                      <span v-if="model.beta" class="badge beta">Beta</span>
                    </div>
                    <div class="model-desc">{{ model.description }}</div>
                  </div>
                  <CheckIcon v-if="selectedModel.id === model.id" class="check-icon" />
                </div>
              </div>
            </div>
          </transition>
        </div>
        
        <div class="messages" ref="messagesRef">
          <!-- 加载动画 - 只在没有内容时显示 -->
          <template v-for="(message, index) in currentMessages" :key="index">
            <ChatMessage
              v-if="message.content || message.role !== 'assistant' || !isStreaming"
              :message="message"
              :is-stream="isStreaming && index === currentMessages.length - 1"
              @retry="retryMessage(index)"
            />
            
            <!-- 加载动画 - 替代空的助手消息 -->
            <div v-else-if="message.role === 'assistant' && !message.content && isStreaming" class="loading-message">
              <div class="avatar">
                <ComputerDesktopIcon class="icon assistant" />
              </div>
              <div class="loading-content">
                <div class="wave-container">
                  <div class="wave"></div>
                  <div class="wave"></div>
                  <div class="wave"></div>
                  <div class="wave"></div>
                  <div class="wave"></div>
                </div>
              </div>
            </div>
          </template>
        </div>
        
        <div class="input-area">
          <div v-if="selectedFiles.length > 0" class="selected-files">
            <div v-for="(file, index) in selectedFiles" :key="index" class="file-item">
              <div class="file-info">
                <DocumentIcon class="icon" />
                <span class="file-name">{{ file.name }}</span>
                <span class="file-size">({{ formatFileSize(file.size) }})</span>
              </div>
              <button class="remove-btn" @click="removeFile(index)">
                <XMarkIcon class="icon" />
              </button>
            </div>
          </div>

          <div class="input-row">
            <div class="file-upload">
              <input 
                type="file" 
                ref="fileInput"
                @change="handleFileUpload"
                accept="image/*,audio/*,video/*"
                multiple
                class="hidden"
              >
              <button 
                class="upload-btn"
                @click="triggerFileInput"
                :disabled="isStreaming"
              >
                <PaperClipIcon class="icon" />
              </button>
            </div>

            <textarea
              v-model="userInput"
              @keydown.enter.prevent="sendMessage"
              :placeholder="getPlaceholder()"
              rows="1"
              ref="inputRef"
            ></textarea>
            <button 
              class="send-button" 
              @click="sendMessage"
              :disabled="isStreaming || (!userInput.trim() && !selectedFiles.length)"
            >
              <PaperAirplaneIcon class="icon" />
            </button>
          </div>
        </div>
      </div>
    </div>
    
    <GroupDialog
      v-model:visible="showGroupDialog"
      :title="editingGroup ? '编辑分组' : '创建分组'"
      :default-value="editingGroup?.name || ''"
      @confirm="handleGroupConfirm"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { useDark } from '@vueuse/core'
import { useUserStore } from '@/stores/user'
import { 
  ChatBubbleLeftRightIcon, 
  PaperAirplaneIcon,
  PlusIcon,
  PaperClipIcon,
  DocumentIcon,
  XMarkIcon,
  PencilIcon,
  TrashIcon,
  ComputerDesktopIcon,
  ChevronDownIcon,
  CheckIcon
} from '@heroicons/vue/24/outline'
import ChatMessage from '../components/ChatMessage.vue'
import GroupDialog from '../components/GroupDialog.vue'
import { chatAPI } from '../services/api'
import { groupAPI } from '../api/group'

const isDark = useDark()
const userStore = useUserStore()
const messagesRef = ref(null)
const inputRef = ref(null)
const userInput = ref('')
const isStreaming = ref(false)
const currentChatId = ref(null)
const currentMessages = ref([])
const chatHistory = ref([])
const fileInput = ref(null)
const selectedFiles = ref([])
const groups = ref([])
const selectedGroupId = ref(null)
const showGroupDialog = ref(false)
const editingGroup = ref(null)

// 模型选择相关
const showModelDropdown = ref(false)
const availableModels = ref([])
const selectedModel = ref({
  id: 'default',
  name: 'Qwen3-千问',
  value: 'qwen-turbo',
  description: '综合AI助手',
  recommended: true
})

// 过滤聊天历史
const filteredChatHistory = computed(() => {
  if (!selectedGroupId.value) {
    return chatHistory.value
  }
  // TODO: 根据分组过滤聊天记录（需要后端支持）
  return chatHistory.value
})

// 切换模型下拉菜单
const toggleModelDropdown = () => {
  showModelDropdown.value = !showModelDropdown.value
}

// 选择模型
const selectModel = (model) => {
  selectedModel.value = model
  showModelDropdown.value = false
  console.log('切换模型:', model.name, model.value)
}

// 点击外部关闭下拉菜单
const handleClickOutside = (event) => {
  const dropdown = document.querySelector('.model-selector')
  if (dropdown && !dropdown.contains(event.target)) {
    showModelDropdown.value = false
  }
}

// 自动调整输入框高度
const adjustTextareaHeight = () => {
  const textarea = inputRef.value
  if (textarea) {
    textarea.style.height = 'auto'
    textarea.style.height = textarea.scrollHeight + 'px'
  }else{
    textarea.style.height = '50px'
  }
}

// 滚动到底部
const scrollToBottom = async () => {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

// 文件类型限制
const FILE_LIMITS = {
  image: { 
    maxSize: 10 * 1024 * 1024,  // 单个文件 10MB
    maxFiles: 3,                 // 最多 3 个文件
    description: '图片文件'
  },
  audio: { 
    maxSize: 10 * 1024 * 1024,  // 单个文件 10MB
    maxDuration: 180,           // 3分钟
    maxFiles: 3,                // 最多 3 个文件
    description: '音频文件'
  },
  video: { 
    maxSize: 150 * 1024 * 1024, // 单个文件 150MB
    maxDuration: 40,            // 40秒
    maxFiles: 3,                // 最多 3 个文件
    description: '视频文件'
  }
}

// 触发文件选择
const triggerFileInput = () => {
  fileInput.value?.click()
}

// 检查文件是否符合要求
const validateFile = async (file) => {
  const type = file.type.split('/')[0]
  const limit = FILE_LIMITS[type]
  
  if (!limit) {
    return { valid: false, error: '不支持的文件类型' }
  }
  
  if (file.size > limit.maxSize) {
    return { valid: false, error: `文件大小不能超过${limit.maxSize / 1024 / 1024}MB` }
  }
  
  if ((type === 'audio' || type === 'video') && limit.maxDuration) {
    try {
      const duration = await getMediaDuration(file)
      if (duration > limit.maxDuration) {
        return { 
          valid: false, 
          error: `${type === 'audio' ? '音频' : '视频'}时长不能超过${limit.maxDuration}秒`
        }
      }
    } catch (error) {
      return { valid: false, error: '无法读取媒体文件时长' }
    }
  }
  
  return { valid: true }
}

// 获取媒体文件时长
const getMediaDuration = (file) => {
  return new Promise((resolve, reject) => {
    const element = file.type.startsWith('audio/') ? new Audio() : document.createElement('video')
    element.preload = 'metadata'
    
    element.onloadedmetadata = () => {
      resolve(element.duration)
      URL.revokeObjectURL(element.src)
    }
    
    element.onerror = () => {
      reject(new Error('无法读取媒体文件'))
      URL.revokeObjectURL(element.src)
    }
    
    element.src = URL.createObjectURL(file)
  })
}

// 修改文件上传处理函数
const handleFileUpload = async (event) => {
  const files = Array.from(event.target.files || [])
  if (!files.length) return
  
  // 检查所有文件类型是否一致
  const firstFileType = files[0].type.split('/')[0]
  const hasInconsistentType = files.some(file => file.type.split('/')[0] !== firstFileType)
  
  if (hasInconsistentType) {
    alert('请选择相同类型的文件（图片、音频或视频）')
    event.target.value = ''
    return
  }

  // 验证所有文件
  for (const file of files) {
    const { valid, error } = await validateFile(file)
    if (!valid) {
      alert(error)
      event.target.value = ''
      selectedFiles.value = []
      return
    }
  }

  // 检查文件总大小
  const totalSize = files.reduce((sum, file) => sum + file.size, 0)
  const limit = FILE_LIMITS[firstFileType]
  if (totalSize > limit.maxSize * 3) { // 允许最多3个文件的总大小
    alert(`${firstFileType === 'image' ? '图片' : firstFileType === 'audio' ? '音频' : '视频'}文件总大小不能超过${(limit.maxSize * 3) / 1024 / 1024}MB`)
    event.target.value = ''
    selectedFiles.value = []
    return
  }

  selectedFiles.value = files
}

// 修改文件输入提示
const getPlaceholder = () => {
  if (selectedFiles.value.length > 0) {
    const type = selectedFiles.value[0].type.split('/')[0]
    const desc = FILE_LIMITS[type].description
    return `已选择 ${selectedFiles.value.length} 个${desc}，可继续输入消息...`
  }
  return '输入消息，可上传图片、音频或视频...'
}

// 发送消息
const sendMessage = async () => {
  if (isStreaming.value) return
  if (!userInput.value.trim() && !selectedFiles.value.length) return
  
  const messageContent = userInput.value.trim()
  const messagesToSend = selectedFiles.value.slice() // 保存文件引用
  
  // 添加用户消息
  const userMessage = {
    role: 'user',
    content: messageContent,
    timestamp: new Date(),
    files: messagesToSend // 保存文件信息用于重试
  }
  currentMessages.value.push(userMessage)
  
  // 清空输入
  userInput.value = ''
  adjustTextareaHeight()
  await scrollToBottom()
  
  // 准备发送数据
  const formData = new FormData()
  if (messageContent) {
    formData.append('prompt', messageContent)
  }
  // 添加模型参数
  formData.append('model', selectedModel.value.value)
  messagesToSend.forEach(file => {
    formData.append('files', file)
  })
  
  // 添加助手消息占位
  const assistantMessage = {
    role: 'assistant',
    content: '',
    timestamp: new Date(),
    metadata: {}  // 初始化 metadata
  }
  currentMessages.value.push(assistantMessage)
  isStreaming.value = true
  
  try {
    const reader = await chatAPI.sendMessage(formData, currentChatId.value)
    const decoder = new TextDecoder('utf-8')
    let accumulatedContent = ''  // 累积内容
    let hasError = false  // 标记是否有错误
    let thinkingStartTime = null  // 思考开始时间
    let thinkingEndTime = null  // 思考结束时间
    
    while (true) {
      try {
        const { value, done } = await reader.read()
        if (done) break
        
        // 累积新内容
        const newContent = decoder.decode(value)
        
        // 检查是否是错误消息
        if (newContent.includes('"type":"error"')) {
          hasError = true
          try {
            // 尝试解析错误消息
            const errorMatch = newContent.match(/"content":"([^"]+)"/)
            if (errorMatch && errorMatch[1]) {
              accumulatedContent = errorMatch[1]
              assistantMessage.role = 'error'
              assistantMessage.type = 'error'
            }
          } catch (e) {
            accumulatedContent = '服务暂时不可用，请稍后重试'
            assistantMessage.role = 'error'
            assistantMessage.type = 'error'
          }
          break
        }
        
        // 检测思考过程的开始和结束
        if (newContent.includes('<think>') && !thinkingStartTime) {
          thinkingStartTime = Date.now()
        }
        if (newContent.includes('</think>') && thinkingStartTime && !thinkingEndTime) {
          thinkingEndTime = Date.now()
        }
        
        accumulatedContent += newContent  // 追加新内容
        
        await nextTick(() => {
          // 更新消息，使用累积的内容
          const updatedMessage = {
            ...assistantMessage,
            content: accumulatedContent,
            metadata: {
              ...assistantMessage.metadata
            }
          }
          
          // 如果思考过程已完成，添加思考时长到 metadata
          if (thinkingEndTime && thinkingStartTime) {
            const duration = (thinkingEndTime - thinkingStartTime) / 1000
            updatedMessage.metadata = {
              ...updatedMessage.metadata,
              thinking_duration: duration
            }
          }
          
          const lastIndex = currentMessages.value.length - 1
          currentMessages.value.splice(lastIndex, 1, updatedMessage)
        })
        await scrollToBottom()
      } catch (readError) {
        console.error('读取流错误:', readError)
        break
      }
    }
    
    // 如果有错误，确保消息被标记为错误类型
    if (hasError) {
      const lastIndex = currentMessages.value.length - 1
      currentMessages.value[lastIndex].role = 'error'
      currentMessages.value[lastIndex].type = 'error'
    }
  } catch (error) {
    console.error('发送消息失败:', error)
    assistantMessage.content = '网络连接失败，请检查网络后重试'
    assistantMessage.role = 'error'
    assistantMessage.type = 'error'
  } finally {
    isStreaming.value = false
    selectedFiles.value = [] // 清空已选文件
    fileInput.value.value = '' // 清空文件输入
    await scrollToBottom()
  }
}

// 重试消息
const retryMessage = async (messageIndex) => {
  // 找到对应的用户消息
  const userMessage = currentMessages.value[messageIndex - 1]
  if (!userMessage || userMessage.role !== 'user') return
  
  // 移除错误消息
  currentMessages.value.splice(messageIndex, 1)
  
  // 准备重新发送
  const formData = new FormData()
  if (userMessage.content) {
    formData.append('prompt', userMessage.content)
  }
  // 添加模型参数
  formData.append('model', selectedModel.value.value)
  if (userMessage.files) {
    userMessage.files.forEach(file => {
      formData.append('files', file)
    })
  }
  
  // 添加新的助手消息占位
  const assistantMessage = {
    role: 'assistant',
    content: '',
    timestamp: new Date(),
    metadata: {}  // 初始化 metadata
  }
  currentMessages.value.push(assistantMessage)
  isStreaming.value = true
  
  try {
    const reader = await chatAPI.sendMessage(formData, currentChatId.value)
    const decoder = new TextDecoder('utf-8')
    let accumulatedContent = ''
    let hasError = false
    let thinkingStartTime = null
    let thinkingEndTime = null
    
    while (true) {
      try {
        const { value, done } = await reader.read()
        if (done) break
        
        const newContent = decoder.decode(value)
        
        if (newContent.includes('"type":"error"')) {
          hasError = true
          try {
            const errorMatch = newContent.match(/"content":"([^"]+)"/)
            if (errorMatch && errorMatch[1]) {
              accumulatedContent = errorMatch[1]
              assistantMessage.role = 'error'
              assistantMessage.type = 'error'
            }
          } catch (e) {
            accumulatedContent = '服务暂时不可用，请稍后重试'
            assistantMessage.role = 'error'
            assistantMessage.type = 'error'
          }
          break
        }
        
        // 检测思考过程的开始和结束
        if (newContent.includes('<think>') && !thinkingStartTime) {
          thinkingStartTime = Date.now()
        }
        if (newContent.includes('</think>') && thinkingStartTime && !thinkingEndTime) {
          thinkingEndTime = Date.now()
        }
        
        accumulatedContent += newContent
        
        await nextTick(() => {
          const updatedMessage = {
            ...assistantMessage,
            content: accumulatedContent,
            metadata: {
              ...assistantMessage.metadata
            }
          }
          
          // 如果思考过程已完成，添加思考时长到 metadata
          if (thinkingEndTime && thinkingStartTime) {
            const duration = (thinkingEndTime - thinkingStartTime) / 1000
            updatedMessage.metadata = {
              ...updatedMessage.metadata,
              thinking_duration: duration
            }
          }
          
          const lastIndex = currentMessages.value.length - 1
          currentMessages.value.splice(lastIndex, 1, updatedMessage)
        })
        await scrollToBottom()
      } catch (readError) {
        console.error('读取流错误:', readError)
        break
      }
    }
    
    if (hasError) {
      const lastIndex = currentMessages.value.length - 1
      currentMessages.value[lastIndex].role = 'error'
      currentMessages.value[lastIndex].type = 'error'
    }
  } catch (error) {
    console.error('重试发送消息失败:', error)
    assistantMessage.content = '网络连接失败，请检查网络后重试'
    assistantMessage.role = 'error'
    assistantMessage.type = 'error'
  } finally {
    isStreaming.value = false
    await scrollToBottom()
  }
}

// 加载特定对话
const loadChat = async (chatId) => {
  currentChatId.value = chatId
  try {
    const messages = await chatAPI.getChatMessages(chatId, 'chat')
    currentMessages.value = messages
  } catch (error) {
    console.error('加载对话消息失败:', error)
    currentMessages.value = []
  }
}

// 加载聊天历史
const loadChatHistory = async () => {
  try {
    const history = await chatAPI.getChatHistory('chat')
    chatHistory.value = history || []
    if (history && history.length > 0) {
      await loadChat(history[0].id)
    } else {
      startNewChat()
    }
  } catch (error) {
    console.error('加载聊天历史失败:', error)
    chatHistory.value = []
    startNewChat()
  }
}

// 开始新对话
const startNewChat = async () => {
  try {
    // 从后端创建新会话并获取ID
    const userId = userStore.userInfo?.id
    if (!userId) {
      console.error('用户未登录')
      return
    }
    
    const sessionId = await chatAPI.createSession(userId, 'chat')
    currentChatId.value = sessionId
    currentMessages.value = []
    
    // 添加新对话到聊天历史列表
    const newChat = {
      id: sessionId,
      title: `对话 ${sessionId.toString().slice(-6)}`
    }
    chatHistory.value = [newChat, ...chatHistory.value]
  } catch (error) {
    console.error('创建新会话失败:', error)
  }
}

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

// 移除文件
const removeFile = (index) => {
  selectedFiles.value = selectedFiles.value.filter((_, i) => i !== index)
  if (selectedFiles.value.length === 0) {
    fileInput.value.value = ''  // 清空文件输入
  }
}

onMounted(() => {
  loadChatHistory()
  loadGroups()
  loadModels()
  adjustTextareaHeight()
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})

// 加载分组列表
const loadGroups = async () => {
  try {
    groups.value = await groupAPI.getGroups()
  } catch (error) {
    console.error('加载分组失败:', error)
    groups.value = []
  }
}

// 加载模型列表
const loadModels = async () => {
  try {
    const models = await chatAPI.getModelList()
    if (models && models.length > 0) {
      // 只显示启用的模型
      const enabledModels = models.filter(m => m.status !== false)
      availableModels.value = enabledModels
      // 选择第一个推荐的模型，如果没有推荐的则选择第一个
      const recommendedModel = enabledModels.find(m => m.recommended)
      selectedModel.value = recommendedModel || enabledModels[0]
    }
  } catch (error) {
    console.error('加载模型列表失败:', error)
    // 如果加载失败，使用默认模型
    availableModels.value = [{
      id: 'default',
      name: 'Qwen3-千问',
      value: 'qwen-turbo',
      description: '综合AI助手',
      recommended: true
    }]
    selectedModel.value = availableModels.value[0]
  }
}

// 选择分组
const selectGroup = (groupId) => {
  selectedGroupId.value = selectedGroupId.value === groupId ? null : groupId
}

// 编辑分组
const editGroup = (group) => {
  editingGroup.value = group
  showGroupDialog.value = true
}

// 删除分组确认
const deleteGroupConfirm = async (groupId) => {
  if (!confirm('确定要删除这个分组吗？')) return
  
  try {
    await groupAPI.deleteGroup(groupId)
    await loadGroups()
    if (selectedGroupId.value === groupId) {
      selectedGroupId.value = null
    }
  } catch (error) {
    console.error('删除分组失败:', error)
    alert('删除分组失败，请稍后重试')
  }
}

// 处理分组对话框确认
const handleGroupConfirm = async (name, icon) => {
  try {
    const userId = userStore.userInfo?.id
    if (!userId) {
      alert('用户未登录')
      return
    }
    
    if (editingGroup.value) {
      // 更新分组
      await groupAPI.updateGroup(editingGroup.value.id, {
        name,
        userId,
        sort: editingGroup.value.sort
      })
    } else {
      // 创建新分组
      await groupAPI.createGroup({
        name,
        userId,
        sort: groups.value.length
      })
    }
    
    await loadGroups()
    editingGroup.value = null
  } catch (error) {
    console.error('保存分组失败:', error)
    alert('保存分组失败，请稍后重试')
  }
}
</script>

<style scoped lang="scss">
.ai-chat {
  position: fixed;  // 修改为固定定位
  top: 64px;       // 导航栏高度
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  background: var(--bg-color);
  overflow: hidden; // 防止页面滚动

  .chat-container {
    flex: 1;
    display: flex;
    max-width: 1800px;
    width: 100%;
    margin: 0 auto;
    padding: 1.5rem 2rem;
    gap: 1.5rem;
    height: 100%;    // 确保容器占满高度
    overflow: hidden; // 防止容器滚动
  }

  .sidebar {
    width: 300px;
    display: flex;
    flex-direction: column;
    background: rgba(255, 255, 255, 0.95);
    backdrop-filter: blur(10px);
    border-radius: 1rem;
    box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
    
    .history-header {
      flex-shrink: 0;
      padding: 1rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid rgba(0, 0, 0, 0.05);
      
      h2 {
        font-size: 1.25rem;
      }
      
      .new-chat {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.5rem 1rem;
        border-radius: 0.5rem;
        background: #007CF0;
        color: white;
        border: none;
        cursor: pointer;
        transition: background-color 0.3s;
        
        &:hover {
          background: #0066cc;
        }
        
        .icon {
          width: 1.25rem;
          height: 1.25rem;
        }
      }
    }
    
    .group-section {
      flex-shrink: 0;
      padding: 1rem;
      border-bottom: 1px solid rgba(0, 0, 0, 0.05);
      
      .group-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 0.75rem;
        
        .group-title {
          font-size: 0.875rem;
          font-weight: 600;
          color: #666;
        }
        
        .add-group-btn {
          width: 1.75rem;
          height: 1.75rem;
          display: flex;
          align-items: center;
          justify-content: center;
          border: none;
          background: rgba(0, 124, 240, 0.1);
          color: #007CF0;
          border-radius: 0.375rem;
          cursor: pointer;
          transition: all 0.2s;
          
          &:hover {
            background: rgba(0, 124, 240, 0.2);
          }
          
          .icon {
            width: 1rem;
            height: 1rem;
          }
        }
      }
      
      .group-list {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
        
        .group-item {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          padding: 0.5rem 0.75rem;
          border-radius: 0.5rem;
          cursor: pointer;
          transition: all 0.2s;
          position: relative;
          
          &:hover {
            background: rgba(0, 0, 0, 0.05);
            
            .group-actions {
              opacity: 1;
            }
          }
          
          &.active {
            background: rgba(0, 124, 240, 0.1);
            color: #007CF0;
          }
          
          .group-icon {
            font-size: 1rem;
          }
          
          .group-name {
            flex: 1;
            font-size: 0.875rem;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
          }
          
          .group-actions {
            display: flex;
            gap: 0.25rem;
            opacity: 0;
            transition: opacity 0.2s;
            
            .action-btn {
              width: 1.5rem;
              height: 1.5rem;
              display: flex;
              align-items: center;
              justify-content: center;
              border: none;
              background: rgba(0, 0, 0, 0.05);
              color: #666;
              border-radius: 0.25rem;
              cursor: pointer;
              transition: all 0.2s;
              
              &:hover {
                background: rgba(0, 124, 240, 0.1);
                color: #007CF0;
              }
              
              &.delete:hover {
                background: rgba(255, 77, 79, 0.1);
                color: #ff4d4f;
              }
              
              .icon {
                width: 0.875rem;
                height: 0.875rem;
              }
            }
          }
        }
      }
    }
    
    .history-list {
      flex: 1;
      overflow-y: auto;
      padding: 0 1rem 1rem;
      
      .history-item {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.75rem;
        border-radius: 0.5rem;
        cursor: pointer;
        transition: background-color 0.3s;
        
        &:hover {
          background: rgba(255, 255, 255, 0.1);
        }
        
        &.active {
          background: rgba(0, 124, 240, 0.1);
        }
        
        .icon {
          width: 1.25rem;
          height: 1.25rem;
        }
        
        .title {
          flex: 1;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }
    }
  }

  .chat-main {
    flex: 1;
    display: flex;
    flex-direction: column;
    background: rgba(255, 255, 255, 0.95);
    backdrop-filter: blur(10px);
    border-radius: 1rem;
    box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
    overflow: hidden;  // 防止内容溢出
    
    .model-selector {
      position: relative;
      padding: 1rem 2rem;
      border-bottom: 1px solid rgba(0, 0, 0, 0.05);
      
      .model-dropdown {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.5rem 1rem;
        background: rgba(0, 124, 240, 0.05);
        border: 1px solid rgba(0, 124, 240, 0.2);
        border-radius: 0.5rem;
        cursor: pointer;
        transition: all 0.2s ease;
        
        &:hover {
          background: rgba(0, 124, 240, 0.1);
          border-color: rgba(0, 124, 240, 0.3);
        }
        
        .model-name {
          font-size: 0.875rem;
          font-weight: 500;
          color: #333;
        }
        
        .icon {
          width: 1rem;
          height: 1rem;
          color: #666;
          transition: transform 0.2s ease;
          
          &.rotate {
            transform: rotate(180deg);
          }
        }
      }
      
      .model-list {
        position: absolute;
        top: calc(100% + 0.5rem);
        left: 2rem;
        min-width: 320px;
        max-width: 400px;
        max-height: 500px;
        overflow-y: auto;
        background: #fff;
        border-radius: 0.75rem;
        box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
        border: 1px solid rgba(0, 0, 0, 0.08);
        z-index: 1000;
        
        .model-category {
          .category-title {
            padding: 0.75rem 1rem;
            font-size: 0.75rem;
            font-weight: 600;
            color: #666;
            text-transform: uppercase;
            letter-spacing: 0.05em;
            background: rgba(0, 0, 0, 0.02);
            border-bottom: 1px solid rgba(0, 0, 0, 0.05);
          }
          
          .model-item {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 0.875rem 1rem;
            cursor: pointer;
            transition: all 0.2s ease;
            border-bottom: 1px solid rgba(0, 0, 0, 0.05);
            
            &:last-child {
              border-bottom: none;
            }
            
            &:hover {
              background: rgba(0, 124, 240, 0.05);
            }
            
            &.active {
              background: rgba(0, 124, 240, 0.08);
              
              .model-title {
                color: #007CF0;
                font-weight: 600;
              }
            }
            
            &.recommended {
              .model-title {
                color: #007CF0;
              }
            }
            
            .model-info {
              flex: 1;
              
              .model-header {
                display: flex;
                align-items: center;
                gap: 0.5rem;
                margin-bottom: 0.25rem;
                
                .model-title {
                  font-size: 0.875rem;
                  font-weight: 500;
                  color: #333;
                }
                
                .badge {
                  padding: 0.125rem 0.375rem;
                  font-size: 0.625rem;
                  font-weight: 500;
                  background: #1890ff;
                  color: #fff;
                  border-radius: 0.25rem;
                  
                  &.beta {
                    background: #ff7875;
                  }
                }
              }
              
              .model-desc {
                font-size: 0.75rem;
                color: #666;
                line-height: 1.4;
              }
            }
            
            .check-icon {
              width: 1.25rem;
              height: 1.25rem;
              color: #007CF0;
              flex-shrink: 0;
            }
          }
        }
      }
    }
    
    .messages {
      flex: 1;
      overflow-y: auto;  // 只允许消息区域滚动
      padding: 2rem;
      
      .loading-message {
        display: flex;
        margin-bottom: 1.5rem;
        gap: 1rem;
        animation: fadeIn 0.3s ease;
        
        .avatar {
          width: 40px;
          height: 40px;
          flex-shrink: 0;
          
          .icon {
            width: 100%;
            height: 100%;
            color: #333;
            background: #f0f0f0;
            padding: 4px;
            border-radius: 8px;
            transition: all 0.3s ease;
            
            &.assistant {
              animation: pulse 2s ease-in-out infinite;
            }
          }
        }
        
        .loading-content {
          display: flex;
          align-items: center;
          padding: 0.75rem 1rem;
          background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
          border: 1px solid #bae6fd;
          border-radius: 1rem 1rem 1rem 0;
          box-shadow: 0 2px 8px rgba(14, 165, 233, 0.08);
          
          .wave-container {
            display: flex;
            align-items: center;
            gap: 0.25rem;
            height: 20px;
            
            .wave {
              width: 3px;
              height: 100%;
              background: linear-gradient(180deg, #0ea5e9 0%, #0284c7 100%);
              border-radius: 1.5px;
              animation: wave 1.2s ease-in-out infinite;
              transform-origin: center;
              
              &:nth-child(1) {
                animation-delay: 0s;
              }
              
              &:nth-child(2) {
                animation-delay: 0.1s;
              }
              
              &:nth-child(3) {
                animation-delay: 0.2s;
              }
              
              &:nth-child(4) {
                animation-delay: 0.3s;
              }
              
              &:nth-child(5) {
                animation-delay: 0.4s;
              }
            }
          }
        }
      }
    }
    
    .input-area {
      flex-shrink: 0;
      padding: 1.5rem 2rem;
      background: rgba(255, 255, 255, 0.98);
      border-top: 1px solid rgba(0, 0, 0, 0.05);
      display: flex;
      flex-direction: column;
      gap: 1rem;

      .selected-files {
        background: rgba(0, 0, 0, 0.02);
        border-radius: 0.75rem;
        padding: 0.75rem;
        border: 1px solid rgba(0, 0, 0, 0.05);
        
        .file-item {
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding: 0.75rem;
          background: #fff;
          border-radius: 0.5rem;
          margin-bottom: 0.75rem;
          border: 1px solid rgba(0, 0, 0, 0.05);
          transition: all 0.2s ease;
          
          &:last-child {
            margin-bottom: 0;
          }
          
          &:hover {
            background: rgba(0, 124, 240, 0.02);
            border-color: rgba(0, 124, 240, 0.2);
          }
          
          .file-info {
            display: flex;
            align-items: center;
            gap: 0.75rem;
            
            .icon {
              width: 1.5rem;
              height: 1.5rem;
              color: #007CF0;
            }
            
            .file-name {
              font-size: 0.875rem;
              color: #333;
              font-weight: 500;
            }
            
            .file-size {
              font-size: 0.75rem;
              color: #666;
              background: rgba(0, 0, 0, 0.05);
              padding: 0.25rem 0.5rem;
              border-radius: 1rem;
            }
          }
          
          .remove-btn {
            padding: 0.375rem;
            border: none;
            background: rgba(0, 0, 0, 0.05);
            color: #666;
            cursor: pointer;
            border-radius: 0.375rem;
            transition: all 0.2s ease;
            
            &:hover {
              background: #ff4d4f;
              color: #fff;
            }
            
            .icon {
              width: 1.25rem;
              height: 1.25rem;
            }
          }
        }
      }

      .input-row {
        display: flex;
        gap: 1rem;
        align-items: flex-end;
        background: #fff;
        padding: 0.75rem;
        border-radius: 1rem;
        border: 1px solid rgba(0, 0, 0, 0.1);
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);

        .file-upload {
          .hidden {
            display: none;
          }
          
          .upload-btn {
            width: 2.5rem;
            height: 2.5rem;
            display: flex;
            align-items: center;
            justify-content: center;
            border: none;
            border-radius: 0.75rem;
            background: rgba(0, 124, 240, 0.1);
            color: #007CF0;
            cursor: pointer;
            transition: all 0.2s ease;
            
            &:hover:not(:disabled) {
              background: rgba(0, 124, 240, 0.2);
            }
            
            &:disabled {
              opacity: 0.5;
              cursor: not-allowed;
            }
            
            .icon {
              width: 1.25rem;
              height: 1.25rem;
            }
          }
        }

        textarea {
          flex: 1;
          resize: none;
          border: none;
          background: transparent;
          padding: 0.75rem;
          color: inherit;
          font-family: inherit;
          font-size: 1rem;
          line-height: 1.5;
          max-height: 150px;
          
          &:focus {
            outline: none;
          }
          
          &::placeholder {
            color: #999;
          }
        }
        
        .send-button {
          width: 2.5rem;
          height: 2.5rem;
          display: flex;
          align-items: center;
          justify-content: center;
          border: none;
          border-radius: 0.75rem;
          background: #007CF0;
          color: white;
          cursor: pointer;
          transition: all 0.2s ease;
          
          &:hover:not(:disabled) {
            background: #0066cc;
            transform: translateY(-1px);
          }
          
          &:disabled {
            background: #ccc;
            cursor: not-allowed;
          }
          
          .icon {
            width: 1.25rem;
            height: 1.25rem;
          }
        }
      }
    }
  }
}

.dark {
  .sidebar {
    background: rgba(40, 40, 40, 0.95);
    box-shadow: 0 4px 6px rgba(0, 0, 0, 0.2);
    
    .history-header {
      border-bottom-color: rgba(255, 255, 255, 0.05);
    }
    
    .group-section {
      border-bottom-color: rgba(255, 255, 255, 0.05);
      
      .group-header {
        .group-title {
          color: #999;
        }
        
        .add-group-btn {
          background: rgba(0, 124, 240, 0.2);
          
          &:hover {
            background: rgba(0, 124, 240, 0.3);
          }
        }
      }
      
      .group-list {
        .group-item {
          &:hover {
            background: rgba(255, 255, 255, 0.05);
          }
          
          &.active {
            background: rgba(0, 124, 240, 0.2);
          }
          
          .group-actions {
            .action-btn {
              background: rgba(255, 255, 255, 0.1);
              color: #999;
              
              &:hover {
                background: rgba(0, 124, 240, 0.2);
                color: #007CF0;
              }
              
              &.delete:hover {
                background: rgba(255, 77, 79, 0.2);
                color: #ff4d4f;
              }
            }
          }
        }
      }
    }
  }
  
  .chat-main {
    background: rgba(40, 40, 40, 0.95);
    box-shadow: 0 4px 6px rgba(0, 0, 0, 0.2);
    
    .model-selector {
      border-bottom-color: rgba(255, 255, 255, 0.05);
      
      .model-dropdown {
        background: rgba(0, 124, 240, 0.1);
        border-color: rgba(0, 124, 240, 0.3);
        
        &:hover {
          background: rgba(0, 124, 240, 0.15);
          border-color: rgba(0, 124, 240, 0.4);
        }
        
        .model-name {
          color: #fff;
        }
        
        .icon {
          color: #999;
        }
      }
      
      .model-list {
        background: #2a2a2a;
        border-color: rgba(255, 255, 255, 0.1);
        box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
        
        .model-category {
          .category-title {
            color: #999;
            background: rgba(255, 255, 255, 0.02);
            border-bottom-color: rgba(255, 255, 255, 0.05);
          }
          
          .model-item {
            border-bottom-color: rgba(255, 255, 255, 0.05);
            
            &:hover {
              background: rgba(0, 124, 240, 0.1);
            }
            
            &.active {
              background: rgba(0, 124, 240, 0.15);
              
              .model-title {
                color: #38bdf8;
              }
            }
            
            &.recommended {
              .model-title {
                color: #38bdf8;
              }
            }
            
            .model-info {
              .model-header {
                .model-title {
                  color: #fff;
                }
                
                .badge {
                  background: #1890ff;
                  
                  &.beta {
                    background: #ff7875;
                  }
                }
              }
              
              .model-desc {
                color: #999;
              }
            }
            
            .check-icon {
              color: #38bdf8;
            }
          }
        }
      }
    }
    
    .messages {
      .loading-message {
        .avatar .icon {
          color: #fff;
          background: #444;
        }
        
        .loading-content {
          background: linear-gradient(135deg, #1e3a5f 0%, #2d4a6f 100%);
          border-color: #3d5a7f;
          box-shadow: 0 2px 8px rgba(14, 165, 233, 0.2);
          
          .wave-container .wave {
            background: linear-gradient(180deg, #38bdf8 0%, #0ea5e9 100%);
          }
        }
      }
    }
    
    .input-area {
      background: rgba(30, 30, 30, 0.98);
      border-top: 1px solid rgba(255, 255, 255, 0.05);
      
      .selected-files {
        background: rgba(255, 255, 255, 0.02);
        border-color: rgba(255, 255, 255, 0.05);
        
        .file-item {
          background: rgba(255, 255, 255, 0.02);
          border-color: rgba(255, 255, 255, 0.05);
          
          &:hover {
            background: rgba(0, 124, 240, 0.1);
            border-color: rgba(0, 124, 240, 0.3);
          }
          
          .file-info {
            .icon {
              color: #007CF0;
            }
            
            .file-name {
              color: #fff;
            }
            
            .file-size {
              color: #999;
              background: rgba(255, 255, 255, 0.1);
            }
          }
          
          .remove-btn {
            background: rgba(255, 255, 255, 0.1);
            color: #999;
            
            &:hover {
              background: #ff4d4f;
              color: #fff;
            }
          }
        }
      }

      .input-row {
        background: rgba(255, 255, 255, 0.02);
        border-color: rgba(255, 255, 255, 0.05);
        box-shadow: none;

        textarea {
          color: #fff;
          
          &::placeholder {
            color: #666;
          }
        }

        .file-upload .upload-btn {
          background: rgba(0, 124, 240, 0.2);
          color: #007CF0;
          
          &:hover:not(:disabled) {
            background: rgba(0, 124, 240, 0.3);
          }
        }
      }
    }
  }
  
  .history-item {
    &:hover {
      background: rgba(255, 255, 255, 0.05) !important;
    }
    
    &.active {
      background: rgba(0, 124, 240, 0.2) !important;
    }
  }
  
  textarea {
    background: rgba(255, 255, 255, 0.05) !important;
    
    &:focus {
      background: rgba(255, 255, 255, 0.1) !important;
    }
  }

  .input-area {
    .file-upload {
      .upload-btn {
        background: rgba(255, 255, 255, 0.1);
        color: #999;
        
        &:hover:not(:disabled) {
          background: rgba(255, 255, 255, 0.2);
          color: #fff;
        }
      }
    }
  }
}

@media (max-width: 768px) {
  .ai-chat {
    .chat-container {
      padding: 0;
    }
    
    .sidebar {
      display: none; // 在移动端隐藏侧边栏
    }
    
    .chat-main {
      border-radius: 0;
    }
  }
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}

@keyframes wave {
  0%, 100% {
    transform: scaleY(0.3);
    opacity: 0.6;
  }
  50% {
    transform: scaleY(1);
    opacity: 1;
  }
}

// 下拉菜单过渡动画
.dropdown-enter-active,
.dropdown-leave-active {
  transition: all 0.2s ease;
}

.dropdown-enter-from {
  opacity: 0;
  transform: translateY(-10px);
}

.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
